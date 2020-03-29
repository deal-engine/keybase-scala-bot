package keybase
import os.Shellable
import zio._

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

  def whoami: IO[String, WhoAmI] = apply("whoami", "--json").map()

  def apply(command: Shellable*): IO[String, String] = ZIO.fromEither {
    os.proc("keybase", command: _*).call(check = false) match {
      case r if r.exitCode == 0 =>
        Right(r.out.text())
      case r =>
        Left(r.err.text())
    }
  }

  def serviceStart: IO[String, String] =
    apply(
      "service",
      "--label",
      "keybase-ammonite-bot",
      "--oneshot-username",
      sys.env("KEYBASE_USERNAME"),
      "--oneshot-paperkey",
      sys.env("KEYBASE_PAPERKEY")
    )

  def chat(json: String): IO[String, String] = json_api("chat")(json)
  def team(json: String): IO[String, String] = json_api("team")(json)

  def json_api(api: String)(json: String): IO[String, String] =
    apply(api, "api", "-m", json)
}

object bot extends zio.App {

  val app =
    for {
      _ <- console.putStrLn("Booting keybase-ammonite bot")
    } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    app.fold(_ => 1, _ => 0)
}
