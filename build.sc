// -*- mode: scala -*-
import mill._, scalalib._, publish._

val crossVersions = Seq("2.13.10")

object keybase extends Cross[Keybase](crossVersions: _*)
class Keybase(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def publishVersion = os.read(os.pwd / "VERSION").trim

  val zioVersion = "2.0.1"

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.7.8",
    ivy"com.lihaoyi::upickle::2.0.0",
    ivy"dev.zio::zio:${zioVersion}",
    ivy"dev.zio::zio-streams:${zioVersion}"
  )

  def artifactName = "keybase-scala-bot"

  def pomSettings = PomSettings(
    description = "An small library for creating keybase bots using scala.",
    organization = "com.github.vic",
    url = "https://github.com/vic/keybase-scala-bot",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("vic", "keybase-scala-bot"),
    developers = Seq(
      Developer("vic", "Victor Borja", "https://github.com/vic")
    )
  )

}
