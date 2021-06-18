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

  def sendMessage(msg: String, to: Seq[String]): ZIO[Console, String, Unit] =
    apply("chat", "send", to, msg).unit

  def apply(command: Shellable*): ZIO[Console, String, String] = {
    val run: ZIO[Any, String, String] =
      ZIO.fromEither {
        os.proc("keybase", command)
          .call(check = false, mergeErrIntoOut = true) match {
          case r if r.exitCode == 0 =>
            Right(r.out.text())
          case r =>
            Left(r.out.text())
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
          def reply(replyMsg: String): ZIO[Console, Throwable, Unit] =
            sendMessage(replyMsg, msg.channel.to).mapError(e => throw new IOException(e))

          def performAction(maybeAction: Option[BotAction]): ZIO[Console with Blocking, Throwable, Unit] =
            maybeAction match {
              case Some(botAction) => middleware.fold(botAction)(_(botAction))(msg, reply)
              case None =>
                console
                  .putStrLn(s"No action found for ${msg.keyword}")
                  .flatMap(_ => reply(s"No action found for ${msg.keyword}, please retry with a valid action"))
            }

          def manageExceptions(results: Either[Throwable, Unit]): ZIO[Console, Nothing, Unit] = results match {
            case Left(e) =>
              val sw = new StringWriter
              e.printStackTrace(new PrintWriter(sw))

              reply(s"An unexpected error occured: ${e.getMessage}").either
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
