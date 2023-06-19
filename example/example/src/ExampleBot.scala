package examplebot

import keybase.Bot
import ExampleActions.actionList

import cats.effect.IOApp

import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.DefaultConfiguration

object ExampleBot extends IOApp.Simple {
  val bot = new Bot(actionList)

  override def run = {
    Configurator.initialize(new DefaultConfiguration())
    bot.app
  }
}
