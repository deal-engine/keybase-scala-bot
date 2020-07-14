package keybase

import java.io.IOException
import scala.util.Try

import zio._

import blocking._
import console._
import stream._
import os._

import keybase._

object cmd {
  import ApiMessage._

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

  def json_api(api: String)(json: String) =
    apply(api, "api", "-m", json)

  def stream_api(): ZStream[Blocking, IOException, ApiMessage] = {
    val subProcess: SubProcess =
      os.proc("keybase", "chat", "api-listen").spawn()
    pprint.pprintln(subProcess.wrapped)

    val outputStream: ZStream[Blocking, IOException, ApiMessage] = Stream
      .fromInputStream(subProcess.stdout.wrapped, 1)
      .aggregate(ZTransducer.utf8Decode)
      .aggregate(ZTransducer.splitOn("\n"))
      .map { msg =>
        Try(upickle.default.read[ApiMessage](msg)).toOption
      }
      .collectSome

    outputStream
  }
}

object bot extends zio.App {
  import cmd._

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
