package discodigg

import scalatags.Text.TypedTag
import zio.{ULayer, ZIO, ZLayer}
import zio.http.*
import zio.ZIO.logInfo
import scalatags.Text.all.*
import zio.http.template.Html

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
            table(
              border := "1",
              thead(
                tr(
                  th("Strežnik"),
                  th("Člani"),
                  th("Prisotnost")
                )
              ),
              tbody(raw(content))
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

  def run = Server.serve(routes)
