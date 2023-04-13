package keybase

import scala.util.Try
import zio._
import os._
import BotTypes._
import zio.stream.{ZPipeline, ZStream}

import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.model.event.AppMentionEvent

import java.io.{IOException, PrintWriter, StringWriter}
import com.slack.api.bolt.handler.BoltEventHandler
import com.slack.api.model.event.MessageEvent
import com.slack.api.app_backend.events.payload.EventsApiPayload
import com.slack.api.bolt.context.builtin.EventContext
import com.slack.api.bolt.response.Response
import java.util.concurrent.LinkedBlockingQueue
import com.slack.api.socket_mode.SocketModeClient
import com.slack.api.app_backend.events.EventHandler
import ujson.True
import java.util.concurrent.CountDownLatch
import java.lang

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

  private lazy val streamKeybase: ZStream[Any,IOException,Either[ApiMessage, MessageSlack]] =
    ZStream
      .fromInputStream(subProcess.stdout.wrapped, 1)
      .via(ZPipeline.utf8Decode)
      .via(ZPipeline.splitOn("\n"))
      .map(_.replace("type", "$type")) // Needed to read polymorphic classes in upickle
      .map(apiMsg => Try { upickle.default.read[ApiMessage](apiMsg) }.toOption)
      .collectSome
      .filter(_.msg.isValidCommand)
      .map(Left.apply)

  private lazy val streamSlack: ZStream[Any,IOException,Either[ApiMessage, MessageSlack]] =
    slackMessageStream().map(Right(_))

  private trait actionHandler {

    val messageContext: MessageContext

    def sendmsg(msg: String, to: Seq[String]): ZIO[Any, CommandFailed, Unit]

    def performAction(maybeAction: Option[BotAction]): ZIO[Any, Throwable, Unit] =
      maybeAction match {
        case Some(botAction) =>
          middleware.fold(botAction)(_(botAction))(messageContext)

        case None =>
          Console
            .printLineError(s"No action found for ${messageContext.message.keyword}")
            .flatMap(_ =>
              sendmsg(
                s"No action found for ${messageContext.message.keyword}, please retry with a valid action",
                messageContext.message match {
                  case MessageSlack(msg) => ???
                  case Message(_, _, channel, _, _) => channel.to
                }
              )
            )
      }

    def manageExceptions(results: Either[Throwable, Unit]): ZIO[Any, Nothing, Unit] = results match {
      case Left(e) =>
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))

        sendmsg(s"An unexpected error occured: ${e.getMessage}", messageContext.message match {
                  case MessageSlack(msg) => ???
                  case Message(_, _, channel, _, _) => channel.to
                })
          .either
          .flatMap(_ => Console.printLineError(sw.toString).orDie)
      case Right(_) => ZIO.unit
    }

    def handleAction = {
      val maybeAction = actions.get(messageContext.message.keyword)

      for {
        _            <- Console.printLine(s"Processing command: ${messageContext.message.input}")
        actionResult <- performAction(maybeAction).either
        _            <- manageExceptions(actionResult)
      } yield ()
    }
  } 

  private lazy val stream_api = {
    println("stream")
    streamSlack
      .mapZIOPar(10) {
        /*
        case Left(ApiMessage(msg)) =>
          val maybeAction = actions.get(msg.keyword)

          for {
            _            <- Console.printLine(s"Processing command: ${msg.input}")
            actionResult <- performAction(maybeAction).either
            _            <- manageExceptions(actionResult)
          } yield ()
        */
        case Right(_) => new actionHandler {

          override val messageContext: MessageContext = ???

          override def sendmsg(msg: String, to: Seq[String]): ZIO[Any,CommandFailed,Unit] = ???

        }.handleAction
      }
    }


  def slackMessageStream(): ZStream[Any, IOException, MessageSlack] = {

    var botToken: String = null
    var appToken: String = null

    try {
      botToken = lang.System.getenv("SLACK_BOT_TOKEN")
      appToken = lang.System.getenv("SLACK_APP_TOKEN")
    } catch {
      case err: Throwable => return ZStream.die(err) : ZStream[Any, IOException, MessageSlack]
    }

    val logger = org.slf4j.LoggerFactory.getLogger("slackMessageStream")

    var app: App = new App(AppConfig.builder().singleTeamBotToken(botToken).build())
    val startSignal: CountDownLatch = new CountDownLatch(1)

    val streamMsg: ZStream[Any,IOException,MessageSlack] = ZStream.async[Any, Throwable, MessageSlack] { cb =>

      logger.debug("Adding callback to stream.");

      app = app.event(classOf[MessageEvent], (req: EventsApiPayload[MessageEvent], ctx) => {
        logger.debug("[MessageEvent] Start.")
        val aa = req.getEvent()
        val slk = MessageSlack(aa)
        logger.debug("[MessageEvent] Adding event to stream.")
        cb(ZIO.succeed(Chunk(slk)))
        logger.debug("[MessageEvent] Ok.")
        ctx.ack()
      });

      logger.debug("Callback added.");
      
      startSignal.countDown()
    }.refineToOrDie

    val slack = ZIO.attemptBlockingIO {
      logger.debug("Awaiting for callback initialization");
      startSignal.await()
      val socketModeApp: SocketModeApp = new SocketModeApp(appToken, SocketModeClient.Backend.JavaWebSocket, app)
      logger.debug("Starting slack socket");
      socketModeApp.start() 
    }

    val effect = slack.fork *> ZIO.succeed(streamMsg)

    ZStream.unwrap(effect)
  }

  val app = {
    /*for {
      username <- System.env("KEYBASE_USERNAME")
      paperkey <- System.env("KEYBASE_PAPERKEY")
      _        <- if (username.isDefined && paperkey.isDefined) logout.flatMap(_ => oneshot) else ZIO.succeed(())
      me       <- whoami
      _        <- Console.printLine(s"Logged in as ${me}")
      _        <- stream_api.runDrain
      _        <- ZIO.never
    } yield ()*/


    val botToken = lang.System.getenv("SLACK_BOT_TOKEN")
    val appToken = lang.System.getenv("SLACK_APP_TOKEN")

    for {
      me       <- whoami
      _        <- Console.printLine(s"Logged in as ${me}")
      _        <- stream_api.runDrain
      _        <- Console.printLine("Stream is empty")
      _        <- ZIO.never
    } yield ()
  }
}
