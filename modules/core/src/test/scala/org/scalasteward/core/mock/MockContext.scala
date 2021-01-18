package org.scalasteward.core.mock

import better.files.File
import cats.effect.{BracketThrow, Sync}
import cats.{Applicative, Parallel}
import io.chrisdavenport.log4cats.Logger
import org.http4s.client.Client
import org.http4s.{HttpApp, Uri}
import org.scalasteward.core.TestInstances.ioContextShift
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.{Cli, Config, Context, SupportedVCS}
import org.scalasteward.core.io._
import org.scalasteward.core.scalafix.MigrationsLoaderTest
import org.scalasteward.core.util.UrlChecker
import org.scalasteward.core.vcs.data.AuthenticatedUser
import scala.concurrent.duration._

object MockContext {
  val args: Cli.Args = Cli.Args(
    workspace = File.temp / "ws",
    reposFile = File.temp / "repos.md",
    defaultRepoConf = Some(File.temp / "default.scala-steward.conf"),
    gitAuthorName = "Bot Doe",
    gitAuthorEmail = "bot@example.org",
    vcsType = SupportedVCS.GitHub,
    vcsApiHost = Uri(),
    vcsLogin = "bot-doe",
    gitAskPass = File.temp / "askpass.sh",
    enableSandbox = Some(true),
    envVar = List(EnvVar("VAR1", "val1"), EnvVar("VAR2", "val2")),
    cacheTtl = 1.hour
  )
  val config: Config = Config.from(args)
  val envVars = List(s"GIT_ASKPASS=${config.gitCfg.gitAskPass}", "VAR1=val1", "VAR2=val2")
  val user: AuthenticatedUser = AuthenticatedUser("scala-steward", "token")

  implicit val mockEffBracketThrow: BracketThrow[MockEff] = Sync[MockEff]
  implicit val mockEffParallel: Parallel[MockEff] = Parallel.identity

  implicit private val client: Client[MockEff] = Client.fromHttpApp(HttpApp.notFound)
  implicit private val fileAlg: FileAlg[MockEff] = new MockFileAlg
  implicit private val logger: Logger[MockEff] = new MockLogger
  implicit private val processAlg: ProcessAlg[MockEff] = MockProcessAlg.create(config.processCfg)
  implicit private val urlChecker: UrlChecker[MockEff] = _ => Applicative[MockEff].pure(false)
  implicit private val workspaceAlg: WorkspaceAlg[MockEff] = new MockWorkspaceAlg

  val context: Context[MockEff] =
    Context.step1[MockEff](config).runA(MigrationsLoaderTest.mockState).unsafeRunSync()
}
