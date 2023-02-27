// -*- mode: scala -*-
import mill._, scalalib._, publish._

val crossVersions = Seq("2.13.10")

object keybase extends Cross[Keybase](crossVersions: _*)
class Keybase(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def publishVersion = os.read(os.pwd / "VERSION").trim

  val zioVersion = "2.0.3"
  val fs2Version = "3.6.1"

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.9.0",
    ivy"com.lihaoyi::upickle::2.0.0",
    ivy"dev.zio::zio:${zioVersion}",
    ivy"dev.zio::zio-streams:${zioVersion}",
    ivy"co.fs2::fs2-core:$fs2Version",
    ivy"co.fs2::fs2-io:$fs2Version",
    ivy"dev.zio::zio-interop-cats:23.0.0.2"
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
