//> using scala "3.7.3"
//> using buildInfo
//> using dep dev.zio::zio:2.1.22
//> using dep dev.zio::zio-streams:2.1.22
//> using dep dev.zio::zio-http:3.5.1
//> using dep dev.zio::zio-cli:0.7.3
//> using dep dev.zio::zio-test:2.1.22
//> using dep dev.zio::zio-logging:2.5.1
//> using dep org.duckdb:duckdb_jdbc:1.4.1.0
//> using dep io.circe::circe-core:0.14.15
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15
//> using dep io.circe::circe-yaml-v12:1.15.0
//> using dep ch.qos.logback:logback-classic:1.5.20
//> using dep dev.zio::zio-logging:2.5.1
//> using dep dev.zio::zio-logging-slf4j2:2.5.1
//> using dep com.lihaoyi::scalatags:0.13.1
//> using dep dev.zio::zio-metrics-connectors:2.5.1
//> using dep dev.zio::zio-metrics-connectors-prometheus:2.5.1

package discodigg

import zio.*
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
import zio.metrics.connectors.{prometheus, MetricsConfig}
import zio.metrics.jvm.DefaultJvmMetrics

object Main extends ZIOCliDefault:

  // Environment and logging setup
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    LoggerSetup.live >>> setConfigProvider(ConfigProvider.envProvider) >>>
      removeDefaultLoggers >>> SLF4J.slf4j

  // Metrics
  private val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))

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
          logInfo(
            s"Booting server on $port with ${BuildInfo.projectVersion.getOrElse("no-version-dev")}"
          ) *> WebServer.run(metricsPort = port.toInt + 1),
          serviceWithZIO[Collector](_.run(refreshInterval)) :: Nil
        )
      }.provide(
        Scope.default,
        Client.default,
        DiscordAPI.live,
        Collector.live,
        Server.defaultWithPort(port.toInt),
        ServersMap.layerFromZIO(serversFromPath(path)),

        // Metrics
        metricsConfig,
        prometheus.publisherLayer,
        prometheus.prometheusLayer,
        Runtime.enableRuntimeMetrics,
        DefaultJvmMetrics.liveV2.unit
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
