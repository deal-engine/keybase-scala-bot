import mill._, scalalib._

import $file.^.build

object example extends ScalaModule {
  def scalaVersion = "2.13.2"

  override def moduleDeps = super.moduleDeps ++ Seq(^.build.keybase)

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.softwaremill.sttp.client::core:2.2.4"
  )
}
