package keybase
import zio._

object bot extends zio.App {

  val app =
    for {
      _ <- console.putStrLn("Booting keybase-ammonite bot")
    } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    app.fold(_ => 1, _ => 0)
}
