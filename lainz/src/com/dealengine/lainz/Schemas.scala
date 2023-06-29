package com.dealengine.lainz

import upickle.default.{ReadWriter => RW, _}
import upickle.implicits.key
import cats.effect.IO
import fs2.Stream

import java.io.IOException
import com.slack.api.model.event.MessageEvent
import com.slack.api.model.event.AppMentionEvent
import scala.util.Try
import scala.jdk.CollectionConverters._
import cats.effect.std.Env

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
  def replyMessage(message: String): IO[Unit]
  def replyAttachment(
      filename: String,
      title: String,
      contents: os.Source
  ): IO[Unit]
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
  def isAttachment: Boolean

  val content: Content
  val sender: Sender

  def input: String
  val isValidCommand: Boolean = input.matches("^!\\w+.*")
  val keyword: String         = input.split(' ').head.drop(1)
  val arguments: Seq[String]  = input.split(' ').toSeq.drop(1)

  def attachmentStream: Stream[IO, String]

  lazy val attachment: IO[String] = attachmentStream.compile.toList.map(_.mkString)
}

case class MessageSlack(
    msg: AppMentionEvent
) extends MessageGeneric {

  override lazy val isAttachment: Boolean = Try { msg.getAttachments().size() > 0 }.getOrElse(false)

  override lazy val input: String = msg.getText().trim().replaceFirst("^<@\\w+>", "").trim()

  override lazy val content: Content = ContentOfText(TextContent(input))
  override val sender: Sender        = Sender(msg.getUser(), msg.getUsername())

  override lazy val attachmentStream: Stream[IO, String] = if (isAttachment && msg.getAttachments().size() != 1) {
    Stream.eval(IO.raiseError(new IOException("Message has more than one attachment")))
  } else if (isAttachment) { // TODO: properly handle attachments
    //fs2.io.
    val getFile = msg
      .getAttachments()
      .asScala
      .flatMap(
        _.getFiles.asScala.map(_.getUrlPrivateDownload)
      )
      .map { url =>
        Env[IO].get("SLACK_BOT_TOKEN").map { token =>
          val process = os.proc("curl", url, "-H", s""""Authorization: Bearer $token"""").spawn()
          fs2.io
            .readInputStream(IO(process.stdout.wrapped), 4096, closeAfterUse = true)
            .through(fs2.text.utf8.decode)
        }
      }
    Stream.emits(getFile).evalMap(identity).flatMap(identity)
  } else Stream.eval(IO.raiseError(new IOException("Message has no attachment")))

}

case class PreMessage(
    id: Int,
    conversation_id: String,
    channel: Channel,
    sender: Sender,
    content: Content
)

object PreMessage {
  implicit val rw: RW[PreMessage] = macroRW[PreMessage]
}

case class Message(
    id: Int,
    conversation_id: String,
    channel: Channel,
    sender: Sender,
    content: Content
) extends MessageGeneric {
  def isAttachment: Boolean = content.isInstanceOf[ContentOfAttachment]

  def input: String = content match {
    case a: ContentOfText       => a.text.body
    case a: ContentOfAttachment => a.attachment.`object`.title
  }

  lazy val attachmentStream: Stream[IO, String] = if (isAttachment) {
    val process = os.proc("keybase", "chat", "download", channel.to, id).spawn()
    fs2.io
      .readInputStream(IO(process.stdout.wrapped), 4096, closeAfterUse = true)
      .through(fs2.text.utf8.decode)
  } else Stream.eval(IO.raiseError(new IOException("Message has no attachment")))

}

case class PreApiMessage(msg: PreMessage)
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

object PreApiMessage {
  implicit val rw: RW[PreApiMessage] = macroRW[PreApiMessage]
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

sealed trait PlatformInit {
  def isSlack   = isBoth
  def isKeybase = isBoth
  def isBoth    = false
}

object PlatformInit {
  case class Keybase() extends PlatformInit {
    override val isKeybase: Boolean = true
  }
  case class Slack() extends PlatformInit {
    override val isSlack: Boolean = true
  }
  case class Both() extends PlatformInit {
    override val isBoth: Boolean = true
  }
}
