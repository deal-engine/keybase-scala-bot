package examplebot

import zio._
import keybase.BotTypes._
import keybase.ContentOfAttachment
import sttp.client.quick._
import upickle.default.read

object ExampleActions {
  type Action = (String, BotAction)

  private val queryBitcoinPrice: BotAction = ctx =>
    for {
      _ <- ctx.replyMessage("Searching current price for bitcoin")
      responseJson <- ZIO.effect {
        val responseBody = quickRequest
          .get(uri"https://api.coindesk.com/v1/bpi/currentprice.json")
          .send()
          .body
        println(responseBody)
        read[CoinbaseResponse](responseBody)
      }
      responseMessage = s"Bitcoin price is currently: ${responseJson.bpi.map {
        case (currency, price) => s"$currency ${price.rate}"
      }.mkString(", ")}"
      _ <- ctx.replyMessage(responseMessage)
    } yield ()

  private val pleaseAttach: BotAction = ctx =>
    ctx.replyAttachment(filename = "find-love-attached.txt", title = "Con amor desde Neza, para el mundo", contents = "<3")

  private val fetchAndPrintAttachment: BotAction = ctx =>
    for {
      fileContent <- ctx.message.attachment
      _ <- ctx.message.content match {
        case a: ContentOfAttachment => ctx.replyAttachment(a.attachment.`object`.filename, a.attachment.`object`.filename, fileContent)
      }
    } yield ()

  private val helpAction: Action = "help" -> { ctx =>
    ctx.replyMessage(s"User ${ctx.message.sender.username} requested help for ${ctx.message.arguments.mkString(" ")}")
  }

  private val sumAction: Action = "sum" -> { ctx => ctx.replyMessage(s"${ctx.message.arguments.map(_.toFloat).sum}") }

  private val bitcoinAction: Action = "bitcoin" -> queryBitcoinPrice

  private val attachmentAction: Action = "attachment" -> fetchAndPrintAttachment

  private val pleaseAttachAction: Action = "please-attach" -> pleaseAttach

  val actionList = Map(helpAction, sumAction, bitcoinAction, attachmentAction, pleaseAttachAction)
}
