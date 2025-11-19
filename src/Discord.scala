package discodigg

import APIError.apiErrorDecoder
import Invite.inviteDecoder
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.parser.parse as parseJson
import io.circe.yaml.v12.parser.parse as parseYaml
import zio.*
import zio.ZIO.{logInfo, logWarningCause}
import zio.http.{Client, Request, URL}
import ZIO.{fromEither, fromOption}
import zio.Cause.Empty

import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import scala.util.{Failure, Try}

enum ServerState:
  case Booting                                            extends ServerState
  case Ok(updatedAt: LocalDateTime = LocalDateTime.now()) extends ServerState
  case Error(message: String)                             extends ServerState
object ServerState:
  given serverStateEncoder: Encoder[ServerState] = Encoder.encodeString.contramap(_.toString)
  given serverStateDecoder: Decoder[ServerState] =
    Decoder.decodeString.emapTry(raw => Failure(new Exception(s"Unimplemented. In: ${raw}")))

final case class DiscordServer(
  name: String,
  inviteUrl: URL,
  iconUrl: Option[URL] = None,
  state: ServerState = ServerState.Booting
)
object DiscordServer:
  given urlDecoder: Decoder[URL]                     = Decoder.decodeString.emapTry(url => URL.decode(url).toTry)
  given discordServerDecoder: Decoder[DiscordServer] =
    Decoder.forProduct4(
      "name",
      "inviteUrl",
      "iconUrl",
      "state"
    ) { (name: String, inviteUrl: URL, iconUrl: Option[URL], stateOpt: Option[ServerState]) =>
      DiscordServer(
        name = name,
        inviteUrl = inviteUrl,
        iconUrl = iconUrl,
        state = stateOpt.getOrElse(ServerState.Booting)
      )
    }

  given URLEncoder: Encoder[URL]                     = Encoder.encodeString.contramap(_.toString)
  given discordServerEncoder: Encoder[DiscordServer] = deriveEncoder

  def fromPath(path: Path): Task[List[DiscordServer]] = for
    data        <- ZIO.attemptBlocking(Files.readString(path, Charset.forName("UTF-8")))
    rawServers  <- fromEither(parseYaml(data))
    serversRoot <-
      fromOption(rawServers.hcursor.downField("servers").focus).orElseFail(new Exception("No servers found."))
    servers     <- ZIO.fromEither(serversRoot.as[List[DiscordServer]])
  yield servers

final case class ServerStats(memberCount: Int, presenceCount: Int)
object ServerStats:
  val empty = ServerStats(0, 0)

final case class Profile(
  id: Option[String],
  name: String,
  icon_hash: String,
  member_count: Int,
  online_count: Int,
  description: Option[String],
  badge: Int,
  badge_color_primary: String,
  badge_color_secondary: String,
  features: Seq[String],
  visibility: Int,
  premium_subscription_count: Int,
  premium_tier: Int
)
object Profile:
  given profileDecoder: Decoder[Profile] = deriveDecoder

final case class Guild(id: String, name: String, premium_subscription_count: Int, icon: String)
object Guild:
  given guildDecoder: Decoder[Guild] = deriveDecoder

final case class Invite(
  code: String,
  profile: Profile,
  guild: Option[Guild],
  approximate_member_count: Option[Int],
  approximate_presence_count: Option[Int]
)
object Invite:
  given inviteDecoder: Decoder[Invite] = deriveDecoder

final case class APIError(
  message: String,
  retry_after: Option[Double]
)
object APIError:
  given apiErrorDecoder: Decoder[APIError] = deriveDecoder

final class DiscordAPI private (private val client: Client):
  private given Decoder[Either[APIError, Invite]] =
    APIError.apiErrorDecoder
      .map[Either[APIError, Invite]](Left(_))
      .or(Invite.inviteDecoder.map[Either[APIError, Invite]](Right(_)))

  private def resolveInvite(code: String): Task[Either[APIError, Invite]] = for
    response <-
      client.batched(
        Request
          .get(s"/invites/$code")
          .addQueryParam("with_counts", "true")
          .addQueryParam("with_expiration", "true")
      )
    body     <- response.body.asString(Charset.forName("UTF-8"))
    json      = parseJson(body).getOrElse(Json.Null)
    result   <- ZIO.fromEither(json.as[Either[APIError, Invite]])
  yield result

  def resolveInviteWithRetry(
    code: String,
    maxRetries: Int = 8,
    baseDelay: Duration = 200.millis,
    maxDelay: Duration = 30.seconds
  ): Task[Invite] =
    def loop(attempt: Int, currentDelay: Duration): Task[Invite] = resolveInvite(code).flatMap:
      case Right(invite) => ZIO.succeed(invite)
      case Left(err)     =>
        val apiHintDelay = err.retry_after.map(s => Duration.fromMillis((s * 1000).toLong))
        val delay        = apiHintDelay.getOrElse(currentDelay)

        if attempt >= maxRetries then
          ZIO.fail(new Exception(s"Rate limited and exceeded max retries ($maxRetries): ${err.message}"))
        else
          logWarningCause(
            s"Code: $code. Message: ${err.message}. Retrying in ${delay.toMillis}ms (attempt ${attempt + 1}/$maxRetries)",
            Cause.empty
          ) *>
            ZIO.sleep(delay) *> loop(attempt + 1, (delay * 2).min(maxDelay))
    loop(0, baseDelay)

object DiscordAPI:

  def resolveInviteWithRetry(
    code: String,
    maxRetries: Int = 4,
    baseDelay: Duration = 200.millis,
    maxDelay: Duration = 30.seconds
  ): RIO[DiscordAPI, Invite] =
    ZIO.serviceWithZIO[DiscordAPI](_.resolveInviteWithRetry(code, maxRetries, baseDelay, maxDelay))

  def live: RLayer[Client, DiscordAPI] = ZLayer.fromZIO:
    for
      discordAPIRoot <- ZIO.fromEither(URL.decode("https://discord.com/api/v10/"))
      client         <- ZIO.serviceWith[Client](_.url(discordAPIRoot))
    yield new DiscordAPI(client)
