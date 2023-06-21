package keybase

import java.util.concurrent.CountDownLatch
import java.lang
import scala.util.Try

import cats.effect.IO
import cats.effect.std.Console
import cats.effect.kernel.Resource
import cats.effect.std.Env
import cats.syntax._
import cats.syntax.all._
import fs2.Stream
import os._

import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.model.event.AppMentionEvent
import com.slack.api.bolt.handler.BoltEventHandler
import com.slack.api.model.event.MessageEvent
import com.slack.api.app_backend.events.payload.EventsApiPayload
import com.slack.api.bolt.context.builtin.EventContext
import com.slack.api.bolt.response.Response
import com.slack.api.socket_mode.SocketModeClient
import com.slack.api.app_backend.events.EventHandler
import java.io.{IOException, PrintWriter, StringWriter}

import keybase.PlatformInit.Both
import keybase.PlatformInit.Keybase
import keybase.PlatformInit.Slack
import BotTypes._

object KeybaseTools {
  def oneshot = apply("oneshot")
  def logout  = apply("logout", "--force")
  def whoami =
    apply("whoami", "--json").map(upickle.default.read[WhoAmI](_))

  def sendMessage(msg: String, to: Seq[String]): IO[Unit] =
    apply("chat", "send", to, msg).void

  def upload(
      filename: String,
      title: String,
      source: Source,
      to: Seq[String]
  ): IO[Unit] = {
    IO.blocking {
      val tmp = os.temp.dir() / filename
      os.write.over(tmp, source)
      tmp
    }.bracket { (tmp: Path) => apply("chat", "upload", "--title", title, to, tmp.toString()).void } { (tmp: Path) =>
      IO.blocking(os.remove(tmp))
    }
  }

  def apply(command: Shellable*): IO[String] = {
    val run: IO[String] = IO.blocking {
      IO.fromEither {
        os.proc("keybase", command)
          .call(check = false, mergeErrIntoOut = true) match {
          case r if r.exitCode == 0 =>
            Right(r.out.text())
          case r =>
            Left(CommandFailed(r, command))
        }
      }
    }.flatten

    Console[IO]
      .println(s"keybase ${command.flatMap(_.value).mkString(" ")}") *>
      run.flatTap(Console[IO].println(_)).onError(e => Console[IO].println(s"ERROR: $e"))
  }
}

class Bot(actions: Map[String, BotAction], middleware: Option[Middleware] = None) {

  lazy val actionSet: Set[String] = actions.keys.toSet.map { str: String => s"!$str" }

  private lazy val subProcess: SubProcess =
    os.proc("keybase", "chat", "api-listen").spawn()

  private lazy val streamKeybase: Stream[IO, Either[ApiMessage, MessageSlack]] =
    fs2.io
      .readInputStream(IO(subProcess.stdout.wrapped), 1)
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines)
      .map(_.replace("type", "$type")) // Needed to read polymorphic classes in upickle
      .map(apiMsg => Try { upickle.default.read[ApiMessage](apiMsg) }.toOption)
      .mapFilter(identity)
      .filter(_.msg.isValidCommand)
      .map(Left.apply)

  private lazy val slackMessageStream: Stream[IO, MessageSlack] = {
    import com.ivmoreau.slack.SlackStream

    val stream = SlackStream[AppMentionEvent](5)

    stream.map(MessageSlack(_))
  }

  private lazy val streamSlack: Stream[IO, Either[ApiMessage, MessageSlack]] =
    slackMessageStream.map(Right(_))

  private trait actionHandler {

    val messageContext: MessageContext

    def sendmsg(msg: String, to: Seq[String]): IO[Unit]

    def performAction(maybeAction: Option[BotAction]): IO[Unit] =
      maybeAction match {
        case Some(botAction) =>
          middleware.fold(botAction)(_(botAction))(messageContext)

        case None =>
          Console[IO]
            .println("No action found for ${messageContext.message.keyword}")
            .flatMap(_ =>
              sendmsg(
                s"No action found for ${messageContext.message.keyword}, please retry with a valid action",
                messageContext.message match {
                  case MessageSlack(msg)            => Seq(msg.getChannel())
                  case Message(_, _, channel, _, _) => channel.to
                }
              )
            )
      }

    def manageExceptions(results: Either[Throwable, Unit]): IO[Unit] = results match {
      case Left(e) =>
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))

        sendmsg(
          s"An unexpected error occured: ${e.getMessage}",
          messageContext.message match {
            case MessageSlack(msg)            => Seq(msg.getChannel())
            case Message(_, _, channel, _, _) => channel.to
          }
        ).attempt
          .flatMap(_ => Console[IO].println(sw.toString))
      case Right(_) => IO.unit
    }

    def handleAction = {
      val maybeAction = actions.get(messageContext.message.keyword)

      for {
        _            <- Console[IO].println(s"Processing command: ${messageContext.message.input}")
        actionResult <- performAction(maybeAction).attempt
        _            <- manageExceptions(actionResult)
      } yield ()
    }
  }

  private def stream_api(platform: PlatformInit) = {
    {
      platform match {
        case Both()    => streamSlack.merge(streamKeybase)
        case Keybase() => streamKeybase
        case Slack()   => streamSlack
      }
    }.parEvalMap(10) {
      case Left(ApiMessage(msg)) =>
        new actionHandler {

          override val messageContext: MessageContext = new MessageContext {

            override val message: MessageGeneric = msg

            override def replyMessage(message: String): IO[Unit] = KeybaseTools.sendMessage(message, msg.channel.to)

            override def replyAttachment(filename: String, title: String, contents: Source): IO[Unit] =
              KeybaseTools.upload(filename, title, contents, msg.channel.to)

          }

          override def sendmsg(msg: String, to: Seq[String]): IO[Unit] = KeybaseTools.sendMessage(msg, to)

        }.handleAction
      case Right(msg) =>
        new actionHandler {

          override val messageContext: MessageContext = new MessageContext {

            override val message: MessageGeneric = msg

            override def replyMessage(message: String): IO[Unit] = {
              import com.ivmoreau.slack.SlackMethods

              val channel = msg.msg.getChannel()

              SlackMethods().flatMap(_.send2Channel(channel)(message))
            }

            override def replyAttachment(filename: String, title: String, contents: Source): IO[Unit] = {
              import com.ivmoreau.slack.SlackMethods
              import com.slack.api.model.Attachment
              import scala.collection.JavaConverters._

              val o       = fs2.io.readOutputStream(4064)(out => IO.blocking(contents.writeBytesTo(out)))
              val c       = o.through(fs2.text.utf8Decode).compile.toList.map(_.mkString)
              val channel = msg.msg.getChannel()

              for {
                methods <- SlackMethods()
                content <- c
                _ <- methods.withMethod(
                  _.filesUpload(_.title(title).filename(filename).content(content).channels(Seq(channel).asJava))
                )
              } yield ()
            }

          }

          override def sendmsg(msg: String, to: Seq[String]): IO[Unit] = {
            import com.ivmoreau.slack.SlackMethods

            SlackMethods().flatMap(_.send2Channel(to.head)(msg))
          }

        }.handleAction
    }
  }

  val useKeybase = for {
    username <- Env[IO].get("KEYBASE_USERNAME")
    paperkey <- Env[IO].get("KEYBASE_PAPERKEY")
    _ <- if (username.isDefined && paperkey.isDefined) KeybaseTools.logout.flatMap(_ => KeybaseTools.oneshot)
    else IO.pure(())
    me <- KeybaseTools.whoami
    _  <- Console[IO].println(s"[KEYBASE] Logged in as ${me}")
  } yield ()

  val useSlack = for {
    bot <- Env[IO].get("SLACK_BOT_TOKEN")
    app <- Env[IO].get("SLACK_APP_TOKEN")
    _   <- if (bot.isDefined && app.isDefined) IO.pure(()) else IO.raiseError(new Exception("MISSING CREDENTIALS SLACK"))
  } yield ()

  def app(platform: PlatformInit) = {
    for {
      _ <- IO.whenA(platform.isKeybase)(useKeybase)
      _ <- IO.whenA(platform.isSlack)(useSlack)
      _ <- Console[IO].println(s"Logged in as }")
      _ <- stream_api(platform).compile.drain
      _ <- Console[IO].println("Stream is empty")
      _ <- IO.never[Nothing]
    } yield ()
  }
}
