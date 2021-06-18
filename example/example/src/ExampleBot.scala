package examplebot

import keybase.Bot
import ExampleActions.actionList

object ExampleBot extends zio.App {
  val bot = new Bot(actionList)

  override def run(args: List[String]) = bot.app.exitCode
}
