package org.scalasteward.core.mock

import better.files.File
import cats.Parallel
import cats.effect.Sync
import io.chrisdavenport.log4cats.Logger
import org.http4s.Uri
import org.scalasteward.core.TestInstances.ioContextShift
import org.scalasteward.core.application.Cli.EnvVar
import org.scalasteward.core.application.{Cli, Config, SupportedVCS}
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.coursier.{CoursierAlg, VersionsCache}
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io._
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.persistence.JsonKeyValueStore
import org.scalasteward.core.repocache.RepoCacheRepository
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafix.{MigrationAlg, MigrationsLoader, MigrationsLoaderTest}
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.update.{ArtifactMigrations, FilterAlg, PruningAlg, UpdateAlg}
import org.scalasteward.core.util.uri._
import org.scalasteward.core.util.{BracketThrow, DateTimeAlg}
import org.scalasteward.core.vcs.VCSRepoAlg
import org.scalasteward.core.vcs.data.AuthenticatedUser
import scala.concurrent.duration._

trait MockContext {
  implicit def config: Config =
    Config.from(
      Cli.Args(
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
        envVar = List(
          EnvVar("VAR1", "val1"),
          EnvVar("VAR2", "val2")
        ),
        cacheTtl = 1.hour
      )
    )

  implicit def mockEffBracketThrow: BracketThrow[MockEff] = Sync[MockEff]
  implicit def mockEffParallel: Parallel[MockEff] = Parallel.identity

  implicit def fileAlg: FileAlg[MockEff] = new MockFileAlg
  implicit def mockLogger: Logger[MockEff] = new MockLogger
  implicit def processAlg: ProcessAlg[MockEff] = MockProcessAlg.create(config.processCfg)
  implicit def workspaceAlg: WorkspaceAlg[MockEff] = new MockWorkspaceAlg

  implicit def coursierAlg: CoursierAlg[MockEff] = CoursierAlg.create
  implicit def dateTimeAlg: DateTimeAlg[MockEff] = DateTimeAlg.create
  implicit def gitAlg: GitAlg[MockEff] = GitAlg.create(config)
  implicit def user: AuthenticatedUser = AuthenticatedUser("scala-steward", "token")
  implicit def vcsRepoAlg: VCSRepoAlg[MockEff] = VCSRepoAlg.create(config)
  implicit def repoConfigAlg: RepoConfigAlg[MockEff] = new RepoConfigAlg[MockEff](config)
  implicit def scalafmtAlg: ScalafmtAlg[MockEff] = ScalafmtAlg.create
  val migrationsLoader: MigrationsLoader[MockEff] = new MigrationsLoader[MockEff]
  implicit def migrationAlg: MigrationAlg = migrationsLoader
    .loadAll(config.scalafixCfg)
    .map(new MigrationAlg(_))
    .runA(MigrationsLoaderTest.mockState)
    .unsafeRunSync()
  implicit def cacheRepository: RepoCacheRepository[MockEff] =
    new RepoCacheRepository[MockEff](new JsonKeyValueStore("repo_cache", "1"))
  implicit def filterAlg: FilterAlg[MockEff] = new FilterAlg[MockEff]
  implicit def versionsCache: VersionsCache[MockEff] =
    new VersionsCache[MockEff](config.cacheTtl, new JsonKeyValueStore("versions", "1"))
  implicit def artifactMigrations: ArtifactMigrations =
    ArtifactMigrations.create[MockEff](config).runA(MockState.empty).unsafeRunSync()
  implicit def updateAlg: UpdateAlg[MockEff] = new UpdateAlg[MockEff]
  implicit def mavenAlg: MavenAlg[MockEff] = MavenAlg.create(config)
  implicit def sbtAlg: SbtAlg[MockEff] = SbtAlg.create(config)
  implicit def millAlg: MillAlg[MockEff] = MillAlg.create
  implicit def buildToolDispatcher: BuildToolDispatcher[MockEff] = BuildToolDispatcher.create
  implicit def editAlg: EditAlg[MockEff] = new EditAlg[MockEff]
  implicit def pullRequestRepository: PullRequestRepository[MockEff] =
    new PullRequestRepository[MockEff](new JsonKeyValueStore("pull_requests", "2"))
  implicit def pruningAlg: PruningAlg[MockEff] = new PruningAlg[MockEff]
}

object MockContext extends MockContext
