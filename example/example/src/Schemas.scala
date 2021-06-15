package examplebot

import upickle.default.{ReadWriter => RW, macroRW}

case class Price(
    code: String,
    symbol: String,
    rate: String,
    description: String,
    rate_float: Float
)

case class CoinbaseResponse(
    bpi: Map[String, Price]
)

object Price {
  implicit val rw: RW[Price] = macroRW
}

object CoinbaseResponse {
  implicit val rw: RW[CoinbaseResponse] = macroRW
}
