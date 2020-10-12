package org.scalasteward.core.mock

import better.files.File
import cats.Parallel
import cats.effect.Sync
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
import org.scalasteward.core.io.{MockFileAlg, MockProcessAlg, MockWorkspaceAlg}
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.persistence.JsonKeyValueStore
import org.scalasteward.core.repocache.RepoCacheRepository
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafix.{MigrationAlg, MigrationsLoader, MigrationsLoaderTest}
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.update.{FilterAlg, GroupMigrations, PruningAlg, UpdateAlg}
import org.scalasteward.core.util.uri._
import org.scalasteward.core.util.{BracketThrow, DateTimeAlg}
import org.scalasteward.core.vcs.VCSRepoAlg
import org.scalasteward.core.vcs.data.AuthenticatedUser
import scala.concurrent.duration._

object MockContext {
  implicit val config: Config =
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
        envVar = List(
          EnvVar("VAR1", "val1"),
          EnvVar("VAR2", "val2")
        ),
        cacheTtl = 1.hour
      )
    )

  implicit val mockEffBracketThrow: BracketThrow[MockEff] = Sync[MockEff]
  implicit val mockEffParallel: Parallel[MockEff] = Parallel.identity

  implicit val fileAlg: MockFileAlg = new MockFileAlg
  implicit val mockLogger: MockLogger = new MockLogger
  implicit val processAlg: MockProcessAlg = new MockProcessAlg(config.processCfg)
  implicit val workspaceAlg: MockWorkspaceAlg = new MockWorkspaceAlg

  implicit val coursierAlg: CoursierAlg[MockEff] = CoursierAlg.create
  implicit val dateTimeAlg: DateTimeAlg[MockEff] = DateTimeAlg.create
  implicit val gitAlg: GitAlg[MockEff] = GitAlg.create
  implicit val user: AuthenticatedUser = AuthenticatedUser("scala-steward", "token")
  implicit val vcsRepoAlg: VCSRepoAlg[MockEff] = VCSRepoAlg.create(config, gitAlg)
  implicit val scalafmtAlg: ScalafmtAlg[MockEff] = ScalafmtAlg.create
  val migrationsLoader: MigrationsLoader[MockEff] = new MigrationsLoader[MockEff]
  implicit val migrationAlg: MigrationAlg = migrationsLoader
    .loadAll(config.scalafixCfg)
    .map(new MigrationAlg(_))
    .runA(MigrationsLoaderTest.mockState)
    .unsafeRunSync()
  implicit val cacheRepository: RepoCacheRepository[MockEff] =
    new RepoCacheRepository[MockEff](new JsonKeyValueStore("repo_cache", "1"))
  implicit val filterAlg: FilterAlg[MockEff] = new FilterAlg[MockEff]
  implicit val versionsCache: VersionsCache[MockEff] =
    new VersionsCache[MockEff](config.cacheTtl, new JsonKeyValueStore("versions", "1"))
  implicit val groupMigrations: GroupMigrations =
    GroupMigrations.create[MockEff].runA(MockState.empty).unsafeRunSync()
  implicit val updateAlg: UpdateAlg[MockEff] = new UpdateAlg[MockEff]
  implicit val mavenAlg: MavenAlg[MockEff] = MavenAlg.create
  implicit val sbtAlg: SbtAlg[MockEff] = SbtAlg.create
  implicit val millAlg: MillAlg[MockEff] = MillAlg.create
  implicit val buildToolDispatcher: BuildToolDispatcher[MockEff] = BuildToolDispatcher.create
  implicit val editAlg: EditAlg[MockEff] = new EditAlg[MockEff]
  implicit val repoConfigAlg: RepoConfigAlg[MockEff] = new RepoConfigAlg[MockEff]
  implicit val pullRequestRepository: PullRequestRepository[MockEff] =
    new PullRequestRepository[MockEff](new JsonKeyValueStore("pull_requests", "1"))
  implicit val pruningAlg: PruningAlg[MockEff] = new PruningAlg[MockEff]
}
