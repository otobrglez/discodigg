package discodigg

import scalatags.Text.TypedTag
import scalatags.Text.all.*
import zio.ZIO
import zio.ZIO.logInfo
import zio.http.*
import zio.http.template.Html
import zio.metrics.connectors.prometheus.PrometheusPublisher

import java.nio.charset.{Charset, StandardCharsets}
import java.time.LocalDateTime

object WebServer:
  private val mainTitle = " 🇸🇮 Slovenski Discord Strežniki 🇸🇮".trim

  private def layout(title: String = mainTitle)(contentBody: TypedTag[String]*) =
    html(
      head(
        meta(charset := "UTF-8"),
        meta(name    := "viewport", content := "width=device-width, initial-scale=1"),
        tag("title")(title),
        tag("style")(
          """/* With love! */
            |:root {
            |  --bg: #0b1220; --fg: #e5e7eb; --muted: #9aa4b2; --link: #60a5fa; --table-border: #233042;
            |  --th-bg: #101828; --th-fg: #e5e7eb; --row-hover: #121a2b; --accent: #34d399;
            |}
            |@media (prefers-color-scheme: light) {
            |  :root {
            |    --bg: #ffffff; --fg: #0f172a; --muted: #475569; --link: #2563eb; --table-border: #e2e8f0;
            |    --th-bg: #f8fafc; --th-fg: #0f172a; --row-hover: #f1f5f9; --accent: #10b981;
            |  }
            |}
            |html, body { font-family: Menlo, sans-serif; font-size: 12pt; line-height: 1.2; font-weight: 400; background: var(--bg); color: var(--fg); }
            |#app { margin: 0 auto; padding: 10px; max-width: 960px; }
            |a { color: var(--link); text-decoration: none; }
            |a:hover { text-decoration: underline; }
            |#app table { width: 100%; border-collapse: collapse; margin: 0 auto; border: 1px solid var(--table-border); }
            |#app table td, #app table th { padding: 5px; border-bottom: 1px solid var(--table-border); }
            |#app table th { background: var(--th-bg); color: var(--th-fg); text-align: left; }
            |#app table tbody tr:hover { background: var(--row-hover); }
            |#app table img.icon { width: 40px; height: 40px; /* border-radius: 8px; */ }
            |.footer { color: var(--muted); }
            |.center { text-align: center; }
            |""".stripMargin
        ),
        script(src   := "https://cdn.jsdelivr.net/npm/htmx.org@2.0.8/dist/htmx.min.js")
      ),
      body(
        div(id := "app", contentBody)
      )
    )

  private def icon(server: DiscordServer): TypedTag[String] = server.iconUrl match
    case Some(value) => img(src := value.toString, cls := "icon")
    case None        => span(cls := "icon")

  private def renderServers: ZIO[ServersMap, String, String] =
    ServersMap.all
      .map(
        _.toList
          .sortBy(-_._2.presenceCount)
          .map { case (server, stats) =>
            val members   = stats.memberCount.toString
            val presences = stats.presenceCount.toString
            s"""<tr>
               |  <td>${icon(server)}</td>
               |  <td>${server.name}</td>
               |  <td style="text-align:center;">$presences / $members</td>
               |</tr>""".stripMargin
          }
          .mkString
      )

  private def routes: Routes[ServersMap, Response] = Routes(
    Method.GET / Root     -> handler(renderServers).map(content =>
      Response.html(
        Html.raw(
          layout(mainTitle) {
            div(
              div(
                cls                   := "wrap",
                attr("hx-trigger")    := "every 3s",
                attr("hx-get")        := "/",
                attr("hx-target")     := "#servers",
                attr("hx-select-oob") := "#servers",
                attr("hx-swap")       := "innerHTML",
                div(
                  id  := "servers",
                  cls := "servers",
                  table(
                    thead(
                      tr(
                        th(colspan := "2", mainTitle),
                        th("Prisotnost / Članstvo")
                      )
                    ),
                    tbody(raw(content)),
                    tfoot(
                      tr(
                        td(colspan := "3", p(style := "text-align:center;", s"Zadnja osvežitev: ${LocalDateTime.now}"))
                      )
                    )
                  )
                )
              ),
              div(
                cls                   := "footer",
                p(
                  style := "text-align: center; margin-top: 20px;",
                  a(href := "https://github.com/otobrglez/discodigg", "otobrglez/discodigg")
                )
              )
            )
          }.render
        )
      )
    ),
    Method.GET / "health" -> Handler.ok
  ).handleError(_ =>
    Response.html(layout("Error") {
      div("Error")
    }.render)
  )

  private val prometheusRoute: Routes[PrometheusPublisher, Response] = Routes(
    Method.GET / "metrics" -> handler {
      ZIO
        .serviceWithZIO[PrometheusPublisher](_.get)
        .map(response =>
          Response(
            status = Status.Ok,
            headers = Headers(Header.ContentType(MediaType.text.plain, charset = Some(Charset.forName("UTF-8")))),
            body = Body.fromString(response, StandardCharsets.UTF_8)
          )
        )
    }
  )

  private def metricServer(metricsPort: Int) = for
    _      <- logInfo(s"Starting internal metrics server on port $metricsPort")
    server <- Server.serve(routes = prometheusRoute).provideSomeLayer(Server.defaultWith(_.port(metricsPort)))
  yield server

  def run(metricsPort: Int) = for
    _      <- metricServer(metricsPort).forkScoped
    server <- Server.serve(routes = routes)
  yield server
