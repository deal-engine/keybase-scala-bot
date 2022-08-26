package examplebot

import keybase.Bot
import ExampleActions.actionList

object ExampleBot extends zio.ZIOAppDefault {
  val bot = new Bot(actionList)

  override def run = bot.app.exitCode
}
