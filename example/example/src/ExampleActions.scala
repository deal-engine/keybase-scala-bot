package examplebot

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

import zio._
import keybase.BotAction
import sttp.client.quick._
import upickle.default.read

object ExampleActions {

  val helpAction = (
    "ayuda",
    BotAction(args => Option(s"este es un ejemplo: $args"))
  )

  val sumAction = (
    "suma",
    BotAction(args => Option(s"${args.split(" ").map(_.toFloat).reduceLeft(_ + _)}"))
  )

  val exchAction = ("tasa", BotAction(response = queryExchangeRates))

  val actionList = Map(helpAction, sumAction, exchAction)

  def parseExchangeArgs(args: String): (String, String, Set[String]) = {
    val argsArray = args.split(" ").map(_.toUpperCase)

    // Check if the last argument is a date
    val datePattern = "(\\d{4}-\\d{2}-\\d{2})".r
    val exchangeDate: String = argsArray.last match {
      case datePattern(date) => date
      case _                 => "latest"
    }

    // Check if all non-date arguments valid currencies
    val currencyPattern = "\\w{3}".r
    val validCurrencies = (exchangeDate match {
      case "latest" => argsArray
      case _        => argsArray.dropRight(1)
    }).filter(currencyPattern.matches(_))

    (exchangeDate, validCurrencies(0), validCurrencies.drop(1).toSet)
  }

  def queryExchangeRates(args: String, replyFunction: BotAction.ReplyFunction): Future[Unit] = {
    (for {
      _ <- replyFunction(s"Recibido: $args")
      (exchangeDate, baseCurrency, exchangeCurrencies) = parseExchangeArgs(args)
      _                                                = assert(exchangeCurrencies.nonEmpty, "Esperaba monedas a convertir")
      _ <- replyFunction(
        s"Buscando cambios para $baseCurrency hacia estas monedas: $exchangeCurrencies, en esta fecha $exchangeDate"
      )
      responseBody = quickRequest
        .get(uri"http://api.openrates.io/$exchangeDate?base=$baseCurrency&symbols=$exchangeCurrencies")
        .send()
        .body
      responseJson = read[CurrencyResponse](responseBody)
      rates        = responseJson.rates.map(rates => rates._1 + ": " + rates._2).mkString(", ")
      _ <- replyFunction(s"[${responseJson.date}] ${responseJson.base} => $rates")
    } yield {}).recoverWith {
      case error => replyFunction(s"Error => $args\n${error.getMessage}")
    }
  }

}
