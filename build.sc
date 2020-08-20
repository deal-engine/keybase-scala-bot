// -*- mode: scala -*-
import $file.build_repos
import mill._, scalalib._, publish._
import $ivy.`com.lihaoyi::mill-contrib-docker:$MILL_VERSION`
import contrib.docker.DockerModule

object keybase extends ScalaModule with PublishModule with DockerModule {
  def publishVersion = os.read(os.pwd / "VERSION").trim

  def scalaVersion = "2.13.2"
  val zioVersion   = "1.0.0-RC20"

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.6.3",
    ivy"com.lihaoyi::upickle::1.1.0",
    ivy"dev.zio::zio:${zioVersion}",
    ivy"dev.zio::zio-streams:${zioVersion}"
  )

  def artifactName = "keybase-scala-bot"

  def pomSettings = PomSettings(
    description = "An small library for creating keybase bots using scala.",
    organization = "io.github.vic",
    url = "https://github.com/vic/keybase-scala-bot",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("vic", "keybase-scala-bot"),
    developers = Seq(
      Developer("vic", "Victor Borja", "https://github.com/vic")
    )
  )

  object docker extends DockerConfig {
    override def tags = List("keybase-amm")

    override def dockerfile = T {
      val jarName = assembly().path.last
      s"""
         |FROM keybaseio/client as base
         |FROM ${baseImage()}
         |COPY --from=base /usr/bin/keybase /usr/bin/keybase
         |COPY --from=base /usr/bin/keybase.sig /usr/bin/keybase.sig
         |COPY $jarName /$jarName
         |ENTRYPOINT ["java", "-cp", "/$jarName", "keybase.bot"]
      """.stripMargin
    }
  }
}
