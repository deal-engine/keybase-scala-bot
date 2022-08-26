package keybase

import zio._

object BotTypes {
  type BotAction  = MessageContext => ZIO[Any, Throwable, Unit]
  type Middleware = BotAction => BotAction
}
