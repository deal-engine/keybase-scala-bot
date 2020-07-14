package keybase

import upickle.implicits.key

case class Channel(
    name: String,
    @key("topic_name") topic_name: Option[String] = None
) {
  val wholeName: String = topic_name match {
    case Some(topicName) => s"$name.$topicName"
    case None => name
  }
}

case class Sender(
    uid: String,
    username: String
)

case class TextContent(
    body: String
)

case class Text(
    text: TextContent
)

case class ApiChatMessage(
    id: Int,
    conversation_id: String,
    channel: Channel,
    sender: Sender,
    content: Text
)

case class ApiMessage(
    msg: ApiChatMessage
)

object Channel {
  implicit val rw = upickle.default.macroRW[Channel]
}

object Sender {
  implicit val rw = upickle.default.macroRW[Sender]
}

object TextContent {
  implicit val rw = upickle.default.macroRW[TextContent]
}

object Text {
  import TextContent._

  implicit val rw = upickle.default.macroRW[Text]
}

object ApiChatMessage {
  import Channel._, Sender._, Text._

  implicit val rw = upickle.default.macroRW[ApiChatMessage]
}

object ApiMessage {
  import ApiChatMessage._

  implicit val rw = upickle.default.macroRW[ApiMessage]
}

case class WhoAmI(
    configured: Boolean,
    registered: Boolean,
    loggedIn: Boolean,
    sessionIsValid: Boolean,
    user: WhoAmI.User,
    deviceName: String
)

object WhoAmI {
  case class User(uid: String, username: String)
  object User {
    implicit val rw = upickle.default.macroRW[User]
  }

  implicit val rw = upickle.default.macroRW[WhoAmI]
}
