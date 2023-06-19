package keybase

import cats.effect.IO

object BotTypes {
  type BotAction  = MessageContext => IO[Unit]
  type Middleware = BotAction => BotAction
}
