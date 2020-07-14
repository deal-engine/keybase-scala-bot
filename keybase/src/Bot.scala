package keybase

import java.io.IOException
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

import zio._

import blocking._
import console._
import stream._
import os._

object Bot {
  def oneshot = apply("oneshot")
  def logout = apply("logout", "--force")

  def whoami =
    apply("whoami", "--json").map(upickle.default.read[WhoAmI](_))

  def sendMessage(msg: String, channel: String) = {
    pprint.pprintln(channel)

    val to: String =
      if(channel contains '.') {
        val team = channel.split('.').init.mkString(".")
        val subchannel = channel.split('.').last
        s"--channel $subchannel $team"
      } else channel

      os.proc("keybase", "chat", "send", to, msg).call()
    }

  def apply(command: Shellable*): ZIO[Console, String, String] = {
    val run: ZIO[Any, String, String] = ZIO
      .fromEither { // TODO: handle possible exceptions raised by os.proc by using ZIO.effect
        os.proc("keybase", command)
          .call(check = false, mergeErrIntoOut = true) match {
          case r if r.exitCode == 0 =>
            Right(r.out.text())
          case r =>
            Left(r.out.text())
        }
      }

    run.tapBoth(console.putStrLn(_), console.putStrLn(_))
  }
}

case class Bot(actions: Map[String, BotAction]) extends zio.App {
  import Bot._

  val actionSet: Set[String] = actions.keys.toSet.map { str: String =>
    s"!$str"
  }

  private val subProcess: SubProcess =
    os.proc("keybase", "chat", "api-listen").spawn()

  private val stream_api: ZStream[Blocking, IOException, (Option[String], Future[Unit])] =
    Stream
      .fromInputStream(subProcess.stdout.wrapped, 1)
      .aggregate(ZTransducer.utf8Decode)
      .aggregate(ZTransducer.splitOn("\n"))
      .map { msg =>
        pprint.pprintln(msg)
        Try(upickle.default.read[ApiMessage](msg)).toOption
      }
      .collectSome
      .map { msg =>
        val input = msg.msg.content.text.body.split(' ')
        val (keyword, argument) = input.headOption.map(_.drop(1)) -> input.tail.mkString(" ")

        pprint.pprintln(keyword -> argument)

        val performActionOption: Option[(Option[String], Future[Unit])] = for {
          keyword <- keyword
          action <- actions.get(keyword)
        } yield (action.logMessage(argument), action.response(argument))

        val performAction =
          performActionOption.getOrElse(Option(s"Unrecognized command: $keyword") -> Future.unit)

        performAction._1.foreach( log =>
          pprint.pprintln(sendMessage(log, msg.msg.channel.wholeName))
        )
        performAction
      }

  val app =
    for {
      me <- whoami
      _ <- console.putStrLn(s"Logged in as ${me}")
      chatOutput = stream_api.tap(res => console.putStrLn(s"Res: $res"))
      _ <- chatOutput.runDrain
      _ <- ZIO.never
    } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    app.fold(_ => ExitCode.failure, _ => ExitCode.success)
}
