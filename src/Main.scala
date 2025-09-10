//> using scala "3.7.3"
//> using buildInfo
//> using dep dev.zio::zio:2.1.21
//> using dep dev.zio::zio-streams:2.1.21
//> using dep dev.zio::zio-http:3.5.0
//> using dep dev.zio::zio-cli:0.7.3
//> using dep dev.zio::zio-test:2.1.21
//> using dep dev.zio::zio-logging:2.5.1
//> using dep org.duckdb:duckdb_jdbc:1.3.2.1
//> using dep io.circe::circe-core:0.14.14
//> using dep io.circe::circe-generic:0.14.14
//> using dep io.circe::circe-parser:0.14.14
//> using dep io.circe::circe-yaml-v12:1.15.0
//> using dep ch.qos.logback:logback-classic:1.5.18
//> using dep dev.zio::zio-logging:2.5.1
//> using dep dev.zio::zio-logging-slf4j2:2.5.1
//> using dep com.lihaoyi::scalatags:0.13.1

package discodigg

import zio.*
import zio.Console.printLine
import zio.Runtime.{removeDefaultLoggers, setConfigProvider}
import zio.ZIO.{logInfo, serviceWithZIO}
import zio.cli.*
import zio.cli.Exists.Yes
import zio.cli.HelpDoc.Span.text
import zio.http.{Client, Server}
import zio.logging.backend.SLF4J

import java.nio.file.Path
import scala.cli.build.BuildInfo
import scala.collection.concurrent.TrieMap

object Main extends ZIOCliDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    LoggerSetup.live >>> setConfigProvider(ConfigProvider.envProvider) >>>
      removeDefaultLoggers >>> SLF4J.slf4j

  // Arguments and options
  private val serverPath: Args[Path]             = Args.file("servers-path", exists = Yes)
  private val serverPort: Options[BigInt]        = Options.integer("port").withDefault(BigInt(8081)).alias("port", "P")
  private val refreshInterval: Options[Duration] = Options.duration("refresh-interval").withDefault(20.seconds)

  // Command composition
  private val serverCommand  = Command("server", serverPort ++ refreshInterval, serverPath)
  private val collectCommand = Command("collect", refreshInterval, serverPath)
  private val command        = Command("discodigg").subcommands(serverCommand, collectCommand)

  val cliApp: CliApp[ZIOAppArgs & Scope, Throwable, Unit] = CliApp.make(
    name = "discodigg",
    version = BuildInfo.projectVersion.getOrElse("no-version-dev"),
    summary = text("Collector of Discord stats."),
    command = command
  ) {
    case ((port: BigInt, refreshInterval: Duration), path: Path) =>
      {
        ZIO.raceFirst(
          logInfo(s"Booting server on ${port}") *> WebServer.run,
          serviceWithZIO[Collector](_.run(refreshInterval)) :: Nil
        )
      }.provide(
        Client.default,
        DiscordAPI.live,
        Collector.live,
        Server.defaultWithPort(port.toInt),
        ServersMap.layerFromZIO(serversFromPath(path))
      )
    case (refreshInterval: Duration, path: Path)                 =>
      serviceWithZIO[Collector](_.run(refreshInterval))
        .provide(
          Client.default,
          DiscordAPI.live,
          Collector.live,
          ServersMap.layerFromZIO(serversFromPath(path))
        )
  }

  private def serversFromPath(path: Path) =
    DiscordServer.fromPath(path).map { servers =>
      TrieMap.from(servers.map(server => server.name -> (server, ServerStats.empty)))
    }
