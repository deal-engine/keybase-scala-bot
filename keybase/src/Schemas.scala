package keybase

import upickle.default.{ReadWriter => RW, _}
import upickle.implicits.key
import zio._
import zio.stream._

import java.io.IOException
import com.slack.api.model.event.MessageEvent

class CommandFailed private[CommandFailed] (message: String) extends Throwable(message)
object CommandFailed {
  def apply(throwable: Throwable): CommandFailed = {
    val e = new CommandFailed(throwable.getMessage)
    e.addSuppressed(throwable)
    e
  }

  def apply(result: os.CommandResult, command: os.Shellable*): CommandFailed = {
    new CommandFailed(s"Command ${command.mkString(" ")} failed. ${result}")
  }
}

trait MessageContext {
  val message: MessageGeneric
  def replyMessage(message: String): ZIO[Any, CommandFailed, Unit]
  def replyAttachment(
      filename: String,
      title: String,
      contents: os.Source
  ): ZIO[Any, CommandFailed, Unit]
}

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


sealed trait MessageGeneric {
  val isAttachment: Boolean

  val input: String
  val isValidCommand: Boolean = input.matches("^!\\w+.*")
  val keyword: String         = input.split(' ').head.drop(1)
  val arguments: Seq[String]  = input.split(' ').drop(1)

  val attachmentStream: ZStream[Any, IOException, String]

  lazy val attachment: ZIO[Any, IOException, String] = attachmentStream.runCollect.map(_.mkString)
}

case class MessageSlack(
  msg: MessageEvent
) extends MessageGeneric {

  override lazy val isAttachment: Boolean = false // Disabled

  override lazy val input: String = msg.getText()

  override lazy val attachmentStream: ZStream[Any,IOException,String] = if (isAttachment) {
    ???
  } else ZStream.fail(new IOException("Message has no attachment"))
  
}

case class Message(
    id: Int,
    conversation_id: String,
    channel: Channel,
    sender: Sender,
    content: Content
) extends MessageGeneric {
  val isAttachment: Boolean = content.isInstanceOf[ContentOfAttachment]

  val input: String = content match {
    case a: ContentOfText       => a.text.body
    case a: ContentOfAttachment => a.attachment.`object`.title
  }

  lazy val attachmentStream: ZStream[Any, IOException, String] = if (isAttachment) {
    val process = os.proc("keybase", "chat", "download", channel.to, id).spawn()
    ZStream
      .fromInputStream(process.stdout.wrapped)
      .via(ZPipeline.utf8Decode)
  } else ZStream.fail(new IOException("Message has no attachment"))

}

case class ApiMessage(msg: Message)

trait ApiMessageGeneric {
  val msg: MessageGeneric
}

object ApiMessageGeneric {
  def apply(api: ApiMessage) = new ApiMessageGeneric {
    val msg: MessageGeneric = api.msg
  }

  def fromMessage(msgP: MessageGeneric) = new ApiMessageGeneric {
    val msg: MessageGeneric = msgP
  }
}

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
