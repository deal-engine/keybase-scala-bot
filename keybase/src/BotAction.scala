package keybase

import scala.concurrent.Future

case class BotAction(
    logMessage: String => Option[String],
    response: String => Future[Unit]
)
