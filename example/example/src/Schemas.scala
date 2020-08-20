package examplebot

import upickle.default.{ReadWriter => RW, macroRW}

case class CurrencyResponse (
  rates: Map[String, Double],
  base: String,
  date: String
)
object CurrencyResponse {
    implicit val rw: RW[CurrencyResponse] = macroRW
}
 