package org.scalasteward.core.mock

import cats.effect.unsafe.implicits.global
import org.http4s.HttpApp
import org.http4s.client.Client
import org.scalasteward.core.application.Context
import org.scalasteward.core.edit.scalafix.ScalafixMigrationsLoaderTest
import org.scalasteward.core.io._
import org.scalasteward.core.mock.MockConfig.config
import org.typelevel.log4cats.Logger

object MockContext {
  implicit private val client: Client[MockEff] = Client.fromHttpApp(HttpApp.notFound)
  implicit private val fileAlg: FileAlg[MockEff] = new MockFileAlg
  implicit private val logger: Logger[MockEff] = new MockLogger
  implicit private val processAlg: ProcessAlg[MockEff] = MockProcessAlg.create(config.processCfg)
  implicit private val workspaceAlg: WorkspaceAlg[MockEff] = new MockWorkspaceAlg

  val context: Context[MockEff] =
    ScalafixMigrationsLoaderTest.mockState.toRef.flatMap(Context.step1(config).run).unsafeRunSync()
}
