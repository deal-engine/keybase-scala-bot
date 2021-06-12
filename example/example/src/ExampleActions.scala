package examplebot

import zio._
import keybase.BotTypes._
import keybase.ContentOfAttachment
import sttp.client.quick._
import upickle.default.read

object ExampleActions {
  type Action = (String, BotAction)

  private val queryBitcoinPrice: BotAction = (_, reply) =>
    for {
      _ <- reply("Searching current price for bitcoin")
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
      _ <- reply(responseMessage)
    } yield ()

  private val fetchAndPrintAttachment: BotAction = (msg, reply) =>
    for {
      fileContent <- msg.attachment
      _ <- reply(s"File ${msg.content match {
        case a: ContentOfAttachment => a.attachment.`object`.filename
      }} content is:")
      _ <- reply(fileContent)
    } yield ()

  private val helpAction: Action = "help" -> { (msg, reply) =>
    reply(s"User ${msg.sender.username} requested help for ${msg.arguments.mkString(" ")}")
  }

  private val sumAction: Action = "sum" -> { (msg, reply) => reply(s"${msg.arguments.map(_.toFloat).sum}") }

  private val bitcoinAction: Action = "bitcoin" -> queryBitcoinPrice

  private val attachmentAction: Action = "attachment" -> fetchAndPrintAttachment

  val actionList = Map(helpAction, sumAction, bitcoinAction, attachmentAction)
}
