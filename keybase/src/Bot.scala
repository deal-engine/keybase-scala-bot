package keybase

import scala.util.Try
import zio._
import os._
import BotTypes._
import zio.stream.{ZPipeline, ZStream}

import java.io.{IOException, PrintWriter, StringWriter}

object Bot {
  def oneshot = apply("oneshot")
  def logout  = apply("logout", "--force")
  def whoami =
    apply("whoami", "--json").map(upickle.default.read[WhoAmI](_))

  def sendMessage(msg: String, to: Seq[String]): ZIO[Any, CommandFailed, Unit] =
    apply("chat", "send", to, msg).unit

  def upload(
      filename: String,
      title: String,
      source: Source,
      to: Seq[String]
  ): ZIO[Any, CommandFailed, Unit] = {
    ZIO.acquireReleaseWith(acquire = ZIO.attemptBlocking {
      val tmp = os.temp.dir() / filename
      os.write.over(tmp, source)
      tmp
    }.mapError(CommandFailed(_)))(release = tmp => ZIO.attemptBlocking(os.remove(tmp)).orDie)(use =
      (tmp: Path) =>
        ZIO.blocking {
          apply("chat", "upload", "--title", title, to, tmp.toString()).unit
        }
    )
  }

  def apply(command: Shellable*): ZIO[Any, CommandFailed, String] = {
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

    Console
      .printLine(s"keybase ${command.flatMap(_.value).mkString(" ")}")
      .flatMap(_ => run.tapBoth(e => Console.printLineError(s"ERROR: $e").orDie, Console.printLine(_)))
      .mapError(CommandFailed(_))
  }
}

class Bot(actions: Map[String, BotAction], middleware: Option[Middleware] = None) {
  import Bot._

  lazy val actionSet: Set[String] = actions.keys.toSet.map { str: String => s"!$str" }

  private lazy val subProcess: SubProcess =
    os.proc("keybase", "chat", "api-listen").spawn()

  private lazy val stream_api =
    ZStream
      .fromInputStream(subProcess.stdout.wrapped, 1)
      .via(ZPipeline.utf8Decode)
      .via(ZPipeline.splitOn("\n"))
      .map(_.replace("type", "$type")) // Needed to read polymorphic classes in upickle
      .map(apiMsg => Try { upickle.default.read[ApiMessage](apiMsg) }.toOption)
      .collectSome
      .filter(_.msg.isValidCommand)
      .mapZIOPar(10) {
        case ApiMessage(msg) =>
          def performAction(maybeAction: Option[BotAction]): ZIO[Any, Throwable, Unit] =
            maybeAction match {
              case Some(botAction) =>
                middleware.fold(botAction)(_(botAction)) {
                  new MessageContext {
                    override lazy val message: Message = msg

                    override def replyMessage(reply: String): ZIO[Any, CommandFailed, Unit] =
                      Bot.sendMessage(reply, msg.channel.to)

                    override def replyAttachment(filename: String, title: String, contents: Source)
                        : ZIO[Any, CommandFailed, Unit] =
                      Bot.upload(filename, title, contents, msg.channel.to)
                  }
                }

              case None =>
                Console
                  .printLineError(s"No action found for ${msg.keyword}")
                  .flatMap(_ =>
                    Bot.sendMessage(
                      s"No action found for ${msg.keyword}, please retry with a valid action",
                      msg.channel.to
                    )
                  )
            }

          def manageExceptions(results: Either[Throwable, Unit]): ZIO[Any, Nothing, Unit] = results match {
            case Left(e) =>
              val sw = new StringWriter
              e.printStackTrace(new PrintWriter(sw))

              Bot
                .sendMessage(s"An unexpected error occured: ${e.getMessage}", msg.channel.to)
                .either
                .flatMap(_ => Console.printLineError(sw.toString).orDie)
            case Right(_) => ZIO.unit
          }

          val maybeAction = actions.get(msg.keyword)

          for {
            _            <- Console.printLine(s"Processing command: ${msg.input}")
            actionResult <- performAction(maybeAction).either
            _            <- manageExceptions(actionResult)
          } yield ()
      }

  val app =
    for {
      username <- System.env("KEYBASE_USERNAME")
      paperkey <- System.env("KEYBASE_PAPERKEY")
      _        <- if (username.isDefined && paperkey.isDefined) logout.flatMap(_ => oneshot) else ZIO.succeed(())
      me       <- whoami
      _        <- Console.printLine(s"Logged in as ${me}")
      _        <- stream_api.runDrain
      _        <- ZIO.never
    } yield ()
}
