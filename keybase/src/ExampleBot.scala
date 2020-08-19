package keybase

import java.io.IOException
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

import zio._

import blocking._
import console._
import stream._
import os._

import sttp.client.quick._

object ExampleActions {
  val helpAction = (
    "ayuda",
    BotAction(
      args => Option(s"este es un ejemplo: $args"),
      _ => Future.unit
    )
  )

  val sumAction = (
    "suma",
    BotAction(
      args => Option(s"${args.split(" ").map(_.toFloat).reduceLeft(_ + _)}"),
      _ => Future.unit
    )
  )

  def queryExchangeRates(args: String): Option[String] = {
    // !tasa eur usd 2020-01-01
    val argsArray = args.split(" ").map(_.toUpperCase)
    
    // Check if the last argument is a date
    val datePattern = "(\\d{4}-\\d{2}-\\d{2})".r
    val exchangeDate: String = argsArray.last match {
      case datePattern(date) => date
      case _ => "latest"
    }

    // Check if all non-date arguments are of currencies
    val currencyPattern = "\\w{3}".r
    val validCurrencies = (exchangeDate match {
      case "latest" => argsArray
      case _ => argsArray.dropRight(1)
    }).filter(currencyPattern.matches(_))
    
    if ( validCurrencies.length < 2 ) return Some("Faltan parámetros o están mal escritos")

    // Create request
    val baseCurrency = validCurrencies(0)
    val exchangeCurrencies = validCurrencies.drop(1).mkString(",")
    val request = quickRequest.get(uri"http://api.openrates.io/$exchangeDate?base=$baseCurrency&symbols=$exchangeCurrencies").send()

    return Some(request.body)
  }

  val exchAction = (
    "tasa",
    BotAction(
      args => Option(s"${args.split(" ").map(_.toFloat).reduceLeft(_ + _)}"),
      _ => Future.unit
    )
  )

  val actionList = Map(helpAction, sumAction, exchAction)

}

object examplebot extends Bot(actions = ExampleActions.actionList)
