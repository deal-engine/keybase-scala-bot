package examplebot

import upickle.default.{ReadWriter => RW, MacroRW}

case class CurrencyResponse (
  rates: Map[String, Double],
  base: String,
  date: String
)
object CurrencyResponse {
    implicit val rw: RW[CurrencyResponse] = MacroRW
}
 