package org.scalasteward.core.mock

import better.files.File
import org.http4s.client.Client
import org.http4s.{HttpApp, Uri}
import org.scalasteward.core.TestInstances.ioContextShift
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.{Cli, Config, Context}
import org.scalasteward.core.edit.scalafix.ScalafixMigrationsLoaderTest
import org.scalasteward.core.io._
import org.scalasteward.core.vcs.VCSType
import org.scalasteward.core.vcs.data.AuthenticatedUser
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

object MockContext {
  val mockRoot: File = File.temp / "scala-steward"
  val args: Cli.Args = Cli.Args(
    workspace = mockRoot / "workspace",
    reposFile = mockRoot / "repos.md",
    defaultRepoConf = Some(mockRoot / "default.scala-steward.conf"),
    gitAuthorName = "Bot Doe",
    gitAuthorEmail = "bot@example.org",
    vcsType = VCSType.GitHub,
    vcsApiHost = Uri(),
    vcsLogin = "bot-doe",
    gitAskPass = mockRoot / "askpass.sh",
    enableSandbox = Some(true),
    envVar = List(EnvVar("VAR1", "val1"), EnvVar("VAR2", "val2")),
    cacheTtl = 1.hour
  )
  val config: Config = Config.from(args)
  val envVars = List(s"GIT_ASKPASS=${config.gitCfg.gitAskPass}", "VAR1=val1", "VAR2=val2")
  val user: AuthenticatedUser = AuthenticatedUser("scala-steward", "token")

  implicit private val client: Client[MockEff] = Client.fromHttpApp(HttpApp.notFound)
  implicit private val fileAlg: FileAlg[MockEff] = new MockFileAlg
  implicit private val logger: Logger[MockEff] = new MockLogger
  implicit private val processAlg: ProcessAlg[MockEff] = MockProcessAlg.create(config.processCfg)
  implicit private val workspaceAlg: WorkspaceAlg[MockEff] = new MockWorkspaceAlg

  val context: Context[MockEff] =
    ScalafixMigrationsLoaderTest.mockState.toRef.flatMap(Context.step1(config).run).unsafeRunSync()
}
