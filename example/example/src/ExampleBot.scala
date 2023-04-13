package examplebot

import keybase.Bot
import ExampleActions.actionList

import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.DefaultConfiguration

object ExampleBot extends zio.ZIOAppDefault {
  val bot = new Bot(actionList)

  override def run = {
    Configurator.initialize(new DefaultConfiguration())
    bot.app.exitCode
  }
}
