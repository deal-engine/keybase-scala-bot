package keybase

import zio._
import zio.blocking.Blocking
import zio.console.Console

object BotTypes {
  type BotAction  = MessageContext => ZIO[Console with Blocking, Throwable, Unit]
  type Middleware = BotAction => BotAction
}
