package keybase

import scala.concurrent.Future

case class BotAction(
    logMessage: String => Option[String] = _ => None,
    response: (String, BotAction.ReplyFunction) => Future[Unit] = (_, _) => Future.unit
)
object BotAction {
  type ReplyFunction = String => Future[Unit]
}
