package keybase

import scala.concurrent.Future

case class BotAction(
    logMessage: String => Option[String],
    response: (String, BotAction.ReplyFunction) => Future[Unit]
)
object BotAction {
    type ReplyFunction = String => Future[Unit]
}
