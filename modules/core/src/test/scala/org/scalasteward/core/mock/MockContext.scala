package org.scalasteward.core.mock

import better.files.File
import cats.effect.unsafe.implicits.global
import org.http4s.HttpApp
import org.http4s.client.Client
import org.scalasteward.core.application.Context
import org.scalasteward.core.application.Context.StewardContext
import org.scalasteward.core.edit.scalafix.ScalafixMigrationsLoader
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.io._
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.repoconfig.RepoConfigLoader
import org.scalasteward.core.update.artifact.ArtifactMigrationsLoader
import org.scalasteward.core.util.UrlCheckerClient
import org.typelevel.log4cats.Logger

object MockContext {
  implicit private val client: Client[MockEff] = Client.fromHttpApp(HttpApp.notFound)
  implicit private val urlCheckerClient: UrlCheckerClient[MockEff] = UrlCheckerClient(client)
  implicit private val fileAlg: FileAlg[MockEff] = new MockFileAlg
  implicit private val logger: Logger[MockEff] = new MockLogger
  implicit private val processAlg: ProcessAlg[MockEff] = MockProcessAlg.create(config.processCfg)
  implicit private val workspaceAlg: WorkspaceAlg[MockEff] = new MockWorkspaceAlg

  val mockState: MockState = MockState.empty.addUris(
    RepoConfigLoader.defaultRepoConfigUrl ->
      ioFileAlg.readResource("default.scala-steward.conf").unsafeRunSync(),
    ArtifactMigrationsLoader.defaultArtifactMigrationsUrl ->
      ioFileAlg.readResource("artifact-migrations.v2.conf").unsafeRunSync(),
    ScalafixMigrationsLoader.defaultScalafixMigrationsUrl ->
      ioFileAlg.readResource("scalafix-migrations.conf").unsafeRunSync()
  )

  val context: Context[MockEff] = mockState.toRef.flatMap(Context.step1(config).run).unsafeRunSync()
  def validateRepoConfigContext(file: File): StewardContext.ValidateRepoConfig[MockEff] =
    Context.initValidateRepoConfig(file)
}
