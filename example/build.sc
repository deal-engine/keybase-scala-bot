import mill._, scalalib._

import mill.define.{Cross, Ctx, Sources, Target}
import $ivy.`com.lihaoyi::mill-contrib-docker:$MILL_VERSION`
import contrib.docker.DockerModule

import $file.^.build

val mainScalaVersion = ^.build.crossVersions.head

object example extends ScalaModule with DockerModule {
  override def scalaVersion = mainScalaVersion

  override def moduleDeps =
    super.moduleDeps ++
      Seq(^.build.keybase(mainScalaVersion))

  override def ivyDeps =
    super.ivyDeps() ++
      Agg(ivy"com.softwaremill.sttp.client::core:2.2.4",
          ivy"org.slf4j:slf4j-api:2.0.7",
          ivy"org.apache.logging.log4j:log4j-api:2.20.0",
          ivy"org.apache.logging.log4j:log4j-core:2.20.0")

  override def runIvyDeps = Agg(
    ivy"org.slf4j:slf4j-log4j12:2.0.7"
  )

  object docker extends DockerConfig {
    override def tags = List("keybase-scala-example")

    override def dockerfile = T {
      val jarName = assembly().path.last
      s"""
         |FROM keybaseio/client as base
         |FROM ${baseImage()}
         |COPY --from=base /usr/bin/keybase /usr/bin/keybase
         |COPY --from=base /usr/bin/keybase.sig /usr/bin/keybase.sig
         |COPY $jarName /$jarName
         |ENTRYPOINT ["java", "-cp", "/$jarName", "examplebot.ExampleBot"]
      """.stripMargin
    }
  }
}
