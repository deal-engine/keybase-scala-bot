package keybase

import os.{CommandResult, Shellable, SubProcess}
import zio._
import zio.system._
import zio.stream._

import scala.util.{Failure, Success, Try}

object API {

  type Keybase = Has[Service]

  trait Service {
    def runCommand(command: Shellable*): ZIO[Any, String, String]
  }

  val live = ZLayer.succeed[Service] {
    new Service {
      override def runCommand(command: Shellable*): ZIO[Any, String, String] =
        ZIO
          .effect(
            os.proc("keybase", command: _*).call(check = false, mergeErrIntoOut = true)
          )
          .mapError(err => err.getMessage)
          .flatMap {
            case commandResult: CommandResult if commandResult.exitCode == 0 =>
              ZIO.succeed(commandResult.out.text())
            case commandResult: CommandResult =>
              ZIO.fail(commandResult.out.text())
          }
    }
  }

  val userName: ZIO[system.System, SecurityException, Option[String]] =
    env("KEYBASE_USERNAME")

  val paperKey: ZIO[system.System, SecurityException, Option[String]] =
    env("KEYBASE_USERNAME")

  def runCommand(command: Shellable*): ZIO[Keybase, String, String] =
    ZIO.service[Service].flatMap(_.runCommand(command: _*))

  val oneshot: ZIO[Keybase, String, Unit]  = runCommand("oneshot").unit
  val logout: ZIO[Keybase, String, String] = runCommand("logout", "--force")

  val apiSubprocess: ZManaged[Any, Throwable, SubProcess] =
    ZManaged.make(
      ZIO.effect[SubProcess](os.proc("keybase", "chat", "api-listen").spawn())
    )(subProcess => ZIO.succeed(subProcess.destroyForcibly()))

  def apiListen(sp: SubProcess) =
    Stream
      .fromInputStream(sp.wrapped.getInputStream)
      .transduce(ZTransducer.utf8Decode)
      .transduce(ZTransducer.splitLines)
      .map(_.replace("\"type\":", "\"$type\":")) // Needed to read polymorphic classes in upickle
      .map(apiMsg => Try { upickle.default.read[ApiMessage](apiMsg) })
      .filterM {
        case Failure(error) =>
          console.putStrLnErr(s"Failed parsing Keybase message: ${error}").as(false)
        case Success(apiMsg) if !apiMsg.msg.isValidCommand =>
          ZIO.succeed(false) // Ignore commands not indented for this bot.
        case _ => ZIO.succeed(true)
      }

}
