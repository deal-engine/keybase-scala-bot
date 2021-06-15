package keybase

import zio._
import zio.blocking.Blocking
import zio.console.Console

object BotTypes {
  type ReplyFunction = String => ZIO[Console, Throwable, Unit]
  type BotAction     = (Message, ReplyFunction) => ZIO[Console with Blocking, Throwable, Unit]
  type Middleware    = BotAction => BotAction
}
