//> using buildInfo
//> using dep dev.zio::zio:2.1.21
//> using dep dev.zio::zio-streams:2.1.21
//> using dep dev.zio::zio-http:3.5.0
//> using dep dev.zio::zio-cli:0.7.3
//> using dep dev.zio::zio-test:2.1.21
//> using dep dev.zio::zio-logging:2.5.1

import zio.*
import zio.cli.*
import zio.cli.HelpDoc.Span.text
import zio.Console.printLine
import zio.cli.Exists.Yes
import scala.cli.build.BuildInfo

import java.nio.file.{Files, Path, Paths}

object Main extends ZIOCliDefault:
  private val serverPath: Args[Path] = Args.file("servers", exists = Yes)

  private val serverCommand                 = Command(
    "server",
    options = Options.integer("port").withDefault(BigInt(8081)),
    args = serverPath
  )
  private val collectCommand: Command[Path] =
    Command("collect", args = serverPath)

  private val command = Command("discodigg").subcommands(serverCommand, collectCommand)

  val cliApp = CliApp.make(
    name = "discodigg",
    version = "0.0.1",
    summary = text("Collector of Discord stats."),
    command = command
  ) {
    case (port: BigInt, path: Path) =>
      printLine(s"port ${port} path ${path} ${BuildInfo}")
    case (path: Path)               =>
      printLine(s"path ${path}")
  }
