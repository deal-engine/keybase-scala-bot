// -*- mode: scala -*-
import $ivy.`io.get-coursier:interface:0.0.21`

// Dont use sonatype's maven-central as it timeouts in travis.
interp.repositories() =
  List(coursierapi.MavenRepository.of("https://jcenter.bintray.com"))

@

import mill._, scalalib._, publish._

import $ivy.`com.lihaoyi::mill-contrib-docker:$MILL_VERSION`
import contrib.docker.DockerModule

object keybase extends ScalaModule with PublishModule with DockerModule {
  def publishVersion = os.read(os.pwd / "VERSION").trim

  // use versions installed from .tool-versions
  // def scalaVersion = scala.util.Properties.versionNumberString
  def scalaVersion = "2.13.2"
  def millVersion = System.getProperty("MILL_VERSION")

  val zioVersion = "1.0.0-RC20"

  override def ivyDeps = Agg(
    ivy"com.lihaoyi:ammonite_${scalaVersion()}:2.1.4",
    ivy"dev.zio::zio:${zioVersion}",
    ivy"dev.zio::zio-streams:${zioVersion}"
  )

  def artifactName = "keybase-ammonite-bot"

  def pomSettings = PomSettings(
    description = "Create keybase-bots from scala",
    organization = "io.github.vic",
    url = "https://github.com/vic/keybase-ammonite-bot",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("vic", "keybase-ammonite-bot"),
    developers = Seq(
      Developer("vic", "Victor Borja", "https://github.com/vic")
    )
  )

  def compileIvyDeps = Agg(
    ivy"com.lihaoyi::mill-scalalib:latest.stable"
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
