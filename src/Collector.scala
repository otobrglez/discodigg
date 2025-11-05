package discodigg

import zio.ZIO.logInfo
import zio.*
import zio.http.*
import zio.metrics.Metric
import zio.stream.ZStream

final class Collector:
  private val membersGauge  = Metric.gauge("discord_members", "Number of all members in the server.")
  private val presenceGauge = Metric.gauge("discord_presence", "Number of members present in the server.")

  private def updateMetrics(server: DiscordServer, serverStats: ServerStats) = for
    _ <- membersGauge.tagged("server", server.name).set(serverStats.memberCount.toDouble)
    _ <- presenceGauge.tagged("server", server.name).set(serverStats.presenceCount.toDouble)
  yield ()

  private def codeFromInviteURL(url: URL): Task[String] =
    ZIO.getOrFail(url.path.segments.lastOption)

  private def collect: RIO[DiscordAPI & ServersMap, Unit] =
    ZStream
      .fromIterableZIO(ServersMap.all)
      .mapZIO((server, _) =>
        codeFromInviteURL(server.inviteUrl).mapBoth(_ => new Exception("No code found in invite url"), server -> _)
      )
      .mapZIO((server, code) =>
        DiscordAPI
          .resolveInviteWithRetry(code)
          .map(invite =>
            server.copy(
              iconUrl = invite.guild.map {
                case g if g.icon.startsWith("a_") =>
                  URL.decode(s"https://cdn.discordapp.com/icons/${g.id}/${g.icon}.gif").toTry.get
                case g                            =>
                  URL.decode(s"https://cdn.discordapp.com/icons/${g.id}/${g.icon}.png").toTry.get
              }
            ) -> ServerStats(
              memberCount = invite.approximate_member_count.getOrElse(0),
              presenceCount = invite.approximate_presence_count.getOrElse(0)
            )
          )
          .tap(updateMetrics)
      )
      .tap((server, stats) =>
        logInfo(s"Collected from ${server.name}. Members: ${stats.memberCount}, presence: ${stats.presenceCount}")
      )
      .runForeach { case pair @ (server, _) => ServersMap.put(server.name, pair) }

  def run(refreshInterval: Duration = 20.seconds): RIO[Scope & ServersMap & DiscordAPI, Unit] = for
    fib <- (collect *> logInfo(s"Done. Sleeping for ${refreshInterval.toSeconds}s"))
             .repeat(Schedule.spaced(refreshInterval))
             .forkScoped
    _   <- logInfo("Started collector.")
    _   <- fib.join
  yield ()

object Collector:
  def live: ULayer[Collector] = ZLayer.succeed(new Collector())
