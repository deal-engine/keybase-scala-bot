package keybase

import java.io.IOException
import scala.concurrent.Future
import scala.util.Try

import zio._

import blocking._
import console._
import stream._
import os._

object examplebot extends Bot(actions = List("ayuda" -> Future.unit).toMap)
