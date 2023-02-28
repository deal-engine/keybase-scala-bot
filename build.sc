// -*- mode: scala -*-
import mill._, scalalib._, publish._
import coursier.maven.MavenRepository

val crossVersions = Seq("2.13.10")

object keybase extends Cross[Keybase](crossVersions: _*)
class Keybase(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
 override def repositoriesTask = T.task { super.repositoriesTask() ++ Seq(
      MavenRepository("https://oss.sonatype.org/content/repositories/releases"),
      MavenRepository("https://jitpack.io")
    )
  }

  def publishVersion = os.read(os.pwd / "VERSION").trim

  val zioVersion = "2.0.3"
  val fs2Version = "3.6.1"

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.8.1",
    ivy"com.lihaoyi::upickle::2.0.0",
    ivy"dev.zio::zio:${zioVersion}",
    ivy"dev.zio::zio-streams:${zioVersion}",
    ivy"co.fs2::fs2-core:$fs2Version",
    // Note: ip4s-core 3.2.0 dependend on cats-effect 3.3.z while other depes used 3.4.z
    // main branch updated the dep but a release hasn't been cut so we use a snapshot
    // Remove exclude when ip4s 3.2.1 or later is released
    ivy"co.fs2::fs2-io:$fs2Version".exclude(
      "com.comcast" -> "ip4s-core_2.13"
    ),
    ivy"com.github.deal-engine.ip4s::ip4s-core:v3.2.1-snapshot-p1",
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
