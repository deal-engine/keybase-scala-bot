package keybase

import scala.util.Try
import zio.{console, _}
import system._
import console._
import stream._
import os._
import BotTypes._
import zio.blocking.Blocking

import java.io.{IOException, PrintWriter, StringWriter}

object Bot {
  def oneshot = apply("oneshot")
  def logout  = apply("logout", "--force")
  def whoami =
    apply("whoami", "--json").map(upickle.default.read[WhoAmI](_))

  def sendMessage(msg: String, to: Seq[String]): ZIO[Console, CommandFailed, Unit] =
    apply("chat", "send", to, msg).unit

  def upload(title: String, source: Source, to: Seq[String]): ZIO[Console with Blocking, CommandFailed, Unit] =
    ZIO.bracket[Console with Blocking, CommandFailed, os.Path, Unit](
      acquire = blocking.effectBlocking(os.temp(contents = source)).mapError(CommandFailed(_)),
      release = (tmp: Path) => blocking.effectBlocking(os.remove(tmp)).orDie,
      use = (tmp: Path) =>
        blocking.blocking {
          apply("chat", "upload", "--title", title, to, tmp.toString()).unit
        }
    )

  def apply(command: Shellable*): ZIO[Console, CommandFailed, String] = {
    val run: ZIO[Any, CommandFailed, String] =
      ZIO.fromEither {
        os.proc("keybase", command)
          .call(check = false, mergeErrIntoOut = true) match {
          case r if r.exitCode == 0 =>
            Right(r.out.text())
          case r =>
            Left(CommandFailed(r, command))
        }
      }

    console
      .putStrLn(s"keybase ${command.flatMap(_.value).mkString(" ")}")
      .flatMap(_ => run.tapBoth(e => console.putStrLn(s"ERROR: $e"), console.putStrLn(_)))
  }
}

class Bot(actions: Map[String, BotAction], middleware: Option[Middleware] = None) {
  import Bot._

  lazy val actionSet: Set[String] = actions.keys.toSet.map { str: String => s"!$str" }

  private lazy val subProcess: SubProcess =
    os.proc("keybase", "chat", "api-listen").spawn()

  private lazy val stream_api =
    Stream
      .fromInputStream(subProcess.stdout.wrapped, 1)
      .aggregate(ZTransducer.utf8Decode)
      .aggregate(ZTransducer.splitOn("\n"))
      .map(_.replace("type", "$type")) // Needed to read polymorphic classes in upickle
      .map(apiMsg => Try { upickle.default.read[ApiMessage](apiMsg) }.toOption)
      .collectSome
      .filter(_.msg.isValidCommand)
      .mapMPar(10) {
        case ApiMessage(msg) =>
          def performAction(maybeAction: Option[BotAction]): ZIO[Console with Blocking, Throwable, Unit] =
            maybeAction match {
              case Some(botAction) =>
                middleware.fold(botAction)(_(botAction)) {
                  new MessageContext {
                    override lazy val message: Message = msg

                    override def replyMessage(reply: String): ZIO[Console, CommandFailed, Unit] =
                      Bot.sendMessage(reply, msg.channel.to)

                    override def replyAttachment(title: String, contents: Source)
                        : ZIO[Console with Blocking, CommandFailed, Unit] =
                      Bot.upload(title, contents, msg.channel.to)
                  }
                }

              case None =>
                console
                  .putStrLn(s"No action found for ${msg.keyword}")
                  .flatMap(_ =>
                    Bot.sendMessage(
                      s"No action found for ${msg.keyword}, please retry with a valid action",
                      msg.channel.to
                    )
                  )
            }

          def manageExceptions(results: Either[Throwable, Unit]): ZIO[Console, Nothing, Unit] = results match {
            case Left(e) =>
              val sw = new StringWriter
              e.printStackTrace(new PrintWriter(sw))

              Bot
                .sendMessage(s"An unexpected error occured: ${e.getMessage}", msg.channel.to)
                .either
                .flatMap(_ => console.putStrLn(sw.toString))
            case Right(_) => ZIO.unit
          }

          val maybeAction = actions.get(msg.keyword)

          for {
            _            <- console.putStrLn(s"Processing command: ${msg.input}")
            actionResult <- performAction(maybeAction).either
            _            <- manageExceptions(actionResult)
          } yield ()
      }

  val app =
    for {
      username <- env("KEYBASE_USERNAME")
      paperkey <- env("KEYBASE_PAPERKEY")
      _        <- if (username.isDefined && paperkey.isDefined) logout.flatMap(_ => oneshot) else ZIO.succeed(())
      me       <- whoami
      _        <- console.putStrLn(s"Logged in as ${me}")
      _        <- stream_api.runDrain
      _        <- ZIO.never
    } yield ()
}
