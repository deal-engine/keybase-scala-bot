package keybase
import os.Shellable
import zio._
import zio.console.Console

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
}

object bot extends zio.App {
  import cmd._

  val app =
    for {
      _ <- console.putStrLn("Booting keybase-ammonite bot")
    } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    oneshot
      .bracket(_ => logout.catchAll(ZIO.succeed(_)))(_ => app)
      .fold(_ => 1, _ => 0)
}
