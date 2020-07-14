package keybase

import java.io.IOException
import scala.concurrent.Future
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

case class Bot(actions: Map[String, Future[Unit]]) extends zio.App {
  import Bot._

  val actionSet: Set[String] = actions.keys.toSet.map { str: String =>
    s"!$str"
  }

  private val subProcess: SubProcess =
    os.proc("keybase", "chat", "api-listen").spawn()

  private val stream_api: ZStream[Blocking, IOException, ApiMessage] =
    Stream
      .fromInputStream(subProcess.stdout.wrapped, 1)
      .aggregate(ZTransducer.utf8Decode)
      .aggregate(ZTransducer.splitOn("\n"))
      .map { msg =>
        Try(upickle.default.read[ApiMessage](msg)).toOption
      }
      .collectSome
      .filter {
        _.msg.content.text.body
          .split(' ')
          .headOption exists (actionSet contains _)
      }

  val app =
    for {
      me <- whoami
      _ <- console.putStrLn(s"Logged in as ${me}")
      chatOutput = stream_api.tap(req => console.putStrLn(s"Req: $req"))
      _ <- chatOutput.runDrain
      _ <- ZIO.never
    } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    app.fold(_ => ExitCode.failure, _ => ExitCode.success)
}
