package keybase
import java.io.IOException

import os._
import zio._
import console._
import stream._

case class WhoAmI(configured: Boolean,
                  registered: Boolean,
                  loggedIn: Boolean,
                  sessionIsValid: Boolean,
                  user: WhoAmI.User,
                  deviceName: String)

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
    val run = ZIO.fromEither {
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

  def foo(api: String)(input: Stream[Nothing, String]) = {

    for {
      subProcess: SubProcess <- ZIO.effect(
        os.proc("keybase", api, "api").spawn()
      )

      consumeInput: Fiber.Runtime[Option[Throwable], Unit] <- input
        .run(ZSink.fromFunctionM { inputJson =>
          ZIO.effect[Unit](subProcess.stdin.writeLine(inputJson))
        })
        .fork

      // {"hola":
      produceOutput <- Stream
        .fromInputStream(subProcess.stdout.wrapped)
        .run(Sink.fromFunction { x: Chunk[Byte] =>
          })

    } yield ()
  }

}

object bot extends zio.App {
  import cmd._

  val app =
    for {
      me <- whoami
      _ <- console.putStrLn(s"Logged in as ${me}")
    } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    oneshot
      .bracket(_ => logout.catchAll(ZIO.succeed(_)))(_ => app)
      .fold(_ => 1, _ => 0)
}
