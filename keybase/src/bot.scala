package keybase

import java.io.IOException

import os._

import zio._
import blocking._
import console._
import stream._

case class WhoAmI(
    configured: Boolean,
    registered: Boolean,
    loggedIn: Boolean,
    sessionIsValid: Boolean,
    user: WhoAmI.User,
    deviceName: String
)

object WhoAmI {
  case class User(uid: String, username: String)
  object User {
    implicit val rw = upickle.default.macroRW[User]
  }

  implicit val rw = upickle.default.macroRW[WhoAmI]
}

object cmd {
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

    val showCmd = console.putStrLn(s"keybase ${command.mkString(" ")}")
    showCmd *> run.tapBoth(console.putStrLn(_), console.putStrLn(_))
  }

  def chat(json: String) = json_api("chat")(json)
  def team(json: String) = json_api("team")(json)

  def json_api(api: String)(json: String) =
    apply(api, "api", "-m", json)

  def stream_api(
      api: String
  )(input: Stream[Nothing, String]): ZStream[Blocking, IOException, String] = {
    val subProcess: SubProcess = os.proc("keybase", api, "api").spawn()
    pprint.pprintln(subProcess.wrapped)

    val outputStream: ZStream[Blocking, IOException, String] = Stream
      .fromInputStream(subProcess.stdout.wrapped, 1)
      .aggregate(ZTransducer.utf8Decode)
      .aggregate(ZTransducer.splitOn("\n"))

    val inputStream = input.mapM { inputJson =>
      ZIO.effectTotal(subProcess.stdin.writeLine(inputJson))
    }

    inputStream.zipRight(outputStream)
  }
}

object bot extends zio.App {
  import cmd._

  val chatInput: Stream[Nothing, String] = Stream("""{"method": "list"}""")

  val app =
    for {
      me <- whoami
      _ <- console.putStrLn(s"Logged in as ${me}")
      chatOutput = stream_api("chat")(
        chatInput // .tap(req => console.putStrLn(s"Req: $req"))
      ).tap(res => console.putStrLn(s"Res: $res"))
      _ <- chatOutput.runDrain
      _ <- ZIO.never
    } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    oneshot
      .bracket(_ => logout.catchAll(ZIO.succeed(_)))(_ => app)
      .fold(_ => ExitCode.failure, _ => ExitCode.success)
}
