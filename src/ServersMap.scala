package discodigg
import zio.*

import scala.collection.concurrent.TrieMap

final class ServersMap private (private val servers: TrieMap[String, (DiscordServer, ServerStats)]):
  def get(name: String): Option[DiscordServer]                   = servers.get(name).map(_._1)
  def put(name: String, server: DiscordServer): Unit             = servers.put(name, (server, ServerStats.empty))
  def put(name: String, tpl: (DiscordServer, ServerStats)): Unit = servers.put(name, tpl)

object ServersMap:
  val empty: ServersMap =
    new ServersMap(TrieMap.empty)

  def emptyLayer: ULayer[ServersMap] =
    ZLayer.succeed(empty)

  def all: ZIO[ServersMap, Nothing, Iterable[(DiscordServer, ServerStats)]] =
    ZIO.serviceWith[ServersMap](_.servers.values)

  def put(name: String, tpl: (DiscordServer, ServerStats)) =
    ZIO.serviceWith[ServersMap](_.put(name, tpl))

  def layerFromZIO(zio: Task[TrieMap[String, (DiscordServer, ServerStats)]]): TaskLayer[ServersMap] =
    ZLayer.fromZIO(zio.map(new ServersMap(_)))
