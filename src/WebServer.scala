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

  private def layout(title: String = "Discord Strežniki")(contentBody: TypedTag[String]*) =
    html(
      head(
        meta(charset := "UTF-8"),
        meta(name    := "viewport", content := "width=device-width, initial-scale=1"),
        tag("title")(title),
        tag("style")(
          """html, body { font-family: sans-serif; font-size: 14pt; line-height: 1.5; }
            |#app { margin: 0 auto; padding: 10px; max-width: 960px; }
            |#app table { border-collapse: collapse; margin: 0 auto; }
            |#app table td { padding: 5px; } """.stripMargin
        )
      ),
      body(
        div(id := "app", contentBody)
      )
    )

  private def renderServers: ZIO[ServersMap, String, String] =
    ServersMap.all
      .map(
        _.toList
          .sortBy(-_._2.presenceCount)
          .map { case (server, stats) =>
            val members   = stats.memberCount.toString
            val presences = stats.presenceCount.toString
            s"""<tr>
               |  <td>${server.name}</td>
               |  <td>$members</td>
               |  <td>$presences</td>
               |</tr>""".stripMargin
          }
          .mkString
      )

  private def routes: Routes[ServersMap, Response] = Routes(
    Method.GET / Root     -> handler(renderServers).map(content =>
      Response.html(
        Html.raw(
          layout("Discord Strežniki") {
            div(
              cls := "wrap",
              div(
                cls := "servers",
                table(
                  thead(
                    tr(
                      th("Strežnik"),
                      th("Člani"),
                      th("Prisotnost")
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
