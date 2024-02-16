// -*- mode: scala -*-
import mill._, scalalib._, publish._
import coursier.maven.MavenRepository

val crossVersions = Seq("2.13.10")

object lainz extends Cross[Lainz](crossVersions: _*)
class Lainz(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def publishVersion = os.read(os.pwd / "VERSION").trim

  val slackVersion = "1.29.1"

  def scalacOptions = super.scalacOptions() ++ Seq("-deprecation")

  def repositoriesTask = T.task { super.repositoriesTask() ++ Seq(
    MavenRepository("https://jitpack.io")
  ) }

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.8.1",
    ivy"com.lihaoyi::upickle::2.0.0",
    ivy"co.fs2::fs2-io:3.7.0",
    ivy"com.github.deal-engine:effectSlack.scala:v2024.2.14",
    ivy"org.slf4j:slf4j-api:2.0.7"
  )

  def artifactName = "lainz-bot-library"

  def pomSettings = PomSettings(
    description = "An small library for creating Keybase/Slack bots using scala.",
    organization = "com.deal-engine",
    url = "https://github.com/deal-engine/keybase-scala-bot",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("deal-engine", "keybase-scala-bot"),
    developers = Seq(
      Developer("vic", "Victor Borja", "https://github.com/vic"),
      Developer("ivanmoreau", "Iván", "https://github.com/ivanmoreau")
    )
  )

}
