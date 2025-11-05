package discodigg

import scalatags.Text.TypedTag
import scalatags.Text.all.*
import zio.ZIO
import zio.ZIO.logInfo
import zio.http.*
import zio.http.template.Html
import zio.metrics.connectors.prometheus.PrometheusPublisher

import java.nio.charset.{Charset, StandardCharsets}

object WebServer:
  private val mainTitle = " 🇸🇮 Slovenski Discord Strežniki 🇸🇮".trim

  private def layout(title: String = mainTitle)(contentBody: TypedTag[String]*) =
    html(
      head(
        meta(charset := "UTF-8"),
        meta(name    := "viewport", content := "width=device-width, initial-scale=1"),
        tag("title")(title),
        tag("style")(
          """html, body { font-family: Menlo, sans-serif; font-size: 14pt; line-height: 1.3; font-weight: 400; }
            |#app { margin: 0 auto; padding: 10px; max-width: 960px; }
            |#app table { border-collapse: collapse; margin: 0 auto; }
            |#app table td, #app table th { padding: 4px; }
            |#app table img.icon { width: 40px; height: 40px; }""".stripMargin
        )
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
              cls := "wrap",
              div(
                cls := "servers",
                table(
                  thead(
                    tr(
                      th(colspan := "2", mainTitle),
                      th("Prisotnost / Članstvo")
                    )
                  ),
                  tbody(raw(content))
                )
              ),
              div(
                cls := "footer",
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
