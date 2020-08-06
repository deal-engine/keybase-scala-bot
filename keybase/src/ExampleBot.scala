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

object examplebot
    extends Bot(
      actions = List(
        "ayuda" -> BotAction(
          args => Option(s"este es un ejemplo: $args"),
          _ => Future.unit
        )
      ).toMap
    )
