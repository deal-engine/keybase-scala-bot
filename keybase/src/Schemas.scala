package keybase

import upickle.default.{ReadWriter => RW, _}
import upickle.implicits.key
import zio.ZIO
import zio.blocking.Blocking
import zio.stream.{Stream, ZStream, ZTransducer}

import java.io.IOException

case class Channel(
    name: String,
    @key("topic_name") topicName: String = null
) {
  val wholeName: String = Option(topicName) match {
    case Some(topicName) => s"$name.$topicName"
    case None            => name
  }

  def to: Seq[String] =
    if (wholeName contains '.') {
      val team       = wholeName.split('.').init.mkString(".")
      val subchannel = wholeName.split('.').last
      Seq("--channel", subchannel, team)
    } else Seq(wholeName)
}

case class Sender(
    uid: String,
    username: String
)

case class TextContent(body: String)

case class AttachmentObject(
    filename: String,
    size: Long,
    title: String
)

case class Attachment(
    `object`: AttachmentObject
)

sealed trait Content
@key("text") case class ContentOfText(text: TextContent)                  extends Content
@key("attachment") case class ContentOfAttachment(attachment: Attachment) extends Content

case class Message(
    id: Int,
    conversation_id: String,
    channel: Channel,
    sender: Sender,
    content: Content
) {
  val isAttachment: Boolean = content.isInstanceOf[ContentOfAttachment]

  val input: String = content match {
    case a: ContentOfText       => a.text.body
    case a: ContentOfAttachment => a.attachment.`object`.title
  }
  val isValidCommand: Boolean = input.matches("^!\\w+.*")
  val keyword: String         = input.split(' ').head.drop(1)
  val arguments: Seq[String]  = input.split(' ').drop(1)

  lazy val attachmentStream: ZStream[Blocking, IOException, String] = if (isAttachment) {
    val process = os.proc("keybase", "chat", "download", channel.to, id).spawn()
    Stream
      .fromInputStream(process.stdout.wrapped)
      .aggregate(ZTransducer.utf8Decode)
  } else ZStream.fail(new IOException("Message has no attachment"))

  lazy val attachment: ZIO[Blocking, IOException, String] = attachmentStream.runCollect.map(_.mkString)
}

case class ApiMessage(msg: Message)

object Channel {
  implicit val rw: RW[Channel] = macroRW[Channel]
}

object Sender {
  implicit val rw: RW[Sender] = macroRW[Sender]
}

object TextContent {
  implicit val rw: RW[TextContent] = macroRW[TextContent]
}

object AttachmentObject {
  implicit val rw: RW[AttachmentObject] = macroRW[AttachmentObject]
}

object Attachment {
  implicit val rw: RW[Attachment] = macroRW[Attachment]
}

object ContentOfText {
  implicit val rw: RW[ContentOfText] = macroRW[ContentOfText]
}

object ContentOfAttachment {
  implicit val rw: RW[ContentOfAttachment] = macroRW[ContentOfAttachment]
}

object Content {
  implicit val rw: RW[Content] = RW.merge(ContentOfText.rw, ContentOfAttachment.rw)
}

object Message {
  implicit val rw: RW[Message] = macroRW[Message]
}

object ApiMessage {
  implicit val rw: RW[ApiMessage] = macroRW[ApiMessage]
}

case class User(uid: String, username: String)
object User {
  implicit val rw: RW[User] = macroRW[User]
}

case class WhoAmI(
    configured: Boolean,
    registered: Boolean,
    loggedIn: Boolean,
    sessionIsValid: Boolean,
    user: User,
    deviceName: String
)

object WhoAmI {
  implicit val rw: RW[WhoAmI] = macroRW[WhoAmI]
}
