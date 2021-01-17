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
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.coursier.{CoursierAlg, VersionsCache}
import org.scalasteward.core.edit.hooks.HookExecutor
import org.scalasteward.core.git.{GenGitAlg, GitAlg}
import org.scalasteward.core.io._
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.persistence.JsonKeyValueStore
import org.scalasteward.core.repocache.RepoCacheRepository
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafix.MigrationsLoaderTest
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.update.{ArtifactMigrations, FilterAlg, UpdateAlg}
import org.scalasteward.core.util.uri._
import org.scalasteward.core.util.{DateTimeAlg, UrlChecker}
import org.scalasteward.core.vcs.VCSRepoAlg
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
    cacheTtl = 1.hour,
    githubAppId = Some(12345678),
    githubAppKeyFile = Some(File("example_app_key"))
  )

  val config: Config = Config.from(args)

  val envVars = List(s"GIT_ASKPASS=${config.gitCfg.gitAskPass}", "VAR1=val1", "VAR2=val2")

  implicit val mockEffBracketThrow: BracketThrow[MockEff] = Sync[MockEff]
  implicit val mockEffParallel: Parallel[MockEff] = Parallel.identity

  implicit val client: Client[MockEff] = Client.fromHttpApp(HttpApp.notFound)
  implicit val fileAlg: FileAlg[MockEff] = new MockFileAlg
  implicit val mockLogger: Logger[MockEff] = new MockLogger
  implicit val processAlg: ProcessAlg[MockEff] = MockProcessAlg.create(config.processCfg)
  implicit val urlChecker: UrlChecker[MockEff] = _ => Applicative[MockEff].pure(false)
  implicit val workspaceAlg: WorkspaceAlg[MockEff] = new MockWorkspaceAlg

  val context: Context[MockEff] =
    Context.effect[MockEff](config).runA(MigrationsLoaderTest.mockState).unsafeRunSync()

  implicit val coursierAlg: CoursierAlg[MockEff] = CoursierAlg.create
  implicit val dateTimeAlg: DateTimeAlg[MockEff] = DateTimeAlg.create
  implicit val gitAlg: GitAlg[MockEff] = GenGitAlg.create(config.gitCfg)
  implicit val hookExecutor: HookExecutor[MockEff] = new HookExecutor[MockEff]
  implicit val user: AuthenticatedUser = AuthenticatedUser("scala-steward", "token")
  implicit val vcsRepoAlg: VCSRepoAlg[MockEff] = new VCSRepoAlg[MockEff](config)
  implicit val repoConfigAlg: RepoConfigAlg[MockEff] = new RepoConfigAlg[MockEff](config)
  implicit val scalafmtAlg: ScalafmtAlg[MockEff] = ScalafmtAlg.create
  implicit val cacheRepository: RepoCacheRepository[MockEff] =
    new RepoCacheRepository[MockEff](new JsonKeyValueStore("repo_cache", "1"))
  implicit val filterAlg: FilterAlg[MockEff] = new FilterAlg[MockEff]
  implicit val versionsCache: VersionsCache[MockEff] =
    new VersionsCache[MockEff](config.cacheTtl, new JsonKeyValueStore("versions", "1"))
  implicit val artifactMigrations: ArtifactMigrations =
    ArtifactMigrations.create[MockEff](config).runA(MockState.empty).unsafeRunSync()
  implicit val updateAlg: UpdateAlg[MockEff] = new UpdateAlg[MockEff]
  implicit val mavenAlg: MavenAlg[MockEff] = MavenAlg.create(config)
  implicit val pullRequestRepository: PullRequestRepository[MockEff] =
    new PullRequestRepository[MockEff](new JsonKeyValueStore("pull_requests", "2"))
}
