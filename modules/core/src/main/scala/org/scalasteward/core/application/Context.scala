/*
 * Copyright 2018-2023 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.application

import better.files.File
import cats.MonadThrow
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import eu.timepit.refined.auto._
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.`User-Agent`
import org.scalasteward.core.application.Config.{ForgeCfg, StewardUsage}
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.buildtool.scalacli.ScalaCliAlg
import org.scalasteward.core.client.ClientConfiguration
import org.scalasteward.core.coursier.{CoursierAlg, VersionsCache}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.edit.hooks.HookExecutor
import org.scalasteward.core.edit.scalafix._
import org.scalasteward.core.edit.update.ScannerAlg
import org.scalasteward.core.forge.github.{GitHubAppApiAlg, GitHubAuthAlg}
import org.scalasteward.core.forge.{ForgeApiAlg, ForgeRepoAlg, ForgeSelection}
import org.scalasteward.core.git.{GenGitAlg, GitAlg}
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.{NurtureAlg, PullRequestRepository, UpdateInfoUrlFinder}
import org.scalasteward.core.persistence.{CachingKeyValueStore, JsonKeyValueStore}
import org.scalasteward.core.repocache._
import org.scalasteward.core.repoconfig.{RepoConfigAlg, RepoConfigLoader, ValidateRepoConfigAlg}
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.update.artifact.{ArtifactMigrationsFinder, ArtifactMigrationsLoader}
import org.scalasteward.core.update.{FilterAlg, PruningAlg, UpdateAlg}
import org.scalasteward.core.util._
import org.scalasteward.core.util.uri._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final class Context[F[_]](implicit
    val artifactMigrationsLoader: ArtifactMigrationsLoader[F],
    val buildToolDispatcher: BuildToolDispatcher[F],
    val coursierAlg: CoursierAlg[F],
    val dateTimeAlg: DateTimeAlg[F],
    val editAlg: EditAlg[F],
    val fileAlg: FileAlg[F],
    val filterAlg: FilterAlg[F],
    val forgeRepoAlg: ForgeRepoAlg[F],
    val gitAlg: GitAlg[F],
    val hookExecutor: HookExecutor[F],
    val httpJsonClient: HttpJsonClient[F],
    val logger: Logger[F],
    val mavenAlg: MavenAlg[F],
    val millAlg: MillAlg[F],
    val nurtureAlg: NurtureAlg[F],
    val pruningAlg: PruningAlg[F],
    val pullRequestRepository: PullRequestRepository[F],
    val refreshErrorAlg: RefreshErrorAlg[F],
    val repoCacheAlg: RepoCacheAlg[F],
    val repoConfigAlg: RepoConfigAlg[F],
    val sbtAlg: SbtAlg[F],
    val scalaCliAlg: ScalaCliAlg[F],
    val scalafixMigrationsFinder: ScalafixMigrationsFinder,
    val scalafixMigrationsLoader: ScalafixMigrationsLoader[F],
    val scalafmtAlg: ScalafmtAlg[F],
    val stewardAlg: StewardAlg[F],
    val updateAlg: UpdateAlg[F],
    val updateInfoUrlFinder: UpdateInfoUrlFinder[F],
    val urlChecker: UrlChecker[F],
    val workspaceAlg: WorkspaceAlg[F]
)

object Context {

  sealed trait StewardContext[F[_]] {
    def runF: F[ExitCode]
  }
  object StewardContext {
    final case class Regular[F[_]](context: Context[F]) extends StewardContext[F] {
      override def runF: F[ExitCode] = context.stewardAlg.runF
    }

    final case class ValidateRepoConfig[F[_]](file: File)(implicit
        val validateRepoConfigAlg: ValidateRepoConfigAlg[F],
        val logger: Logger[F]
    ) extends StewardContext[F] {
      override def runF: F[ExitCode] = validateRepoConfigAlg.validateAndReport(file)
    }
  }

  def step0[F[_]](
      usage: Config.StewardUsage
  )(implicit F: Async[F]): Resource[F, StewardContext[F]] =
    for {
      logger0 <- Resource.eval(Slf4jLogger.fromName[F]("org.scalasteward.core"))
      _ <- Resource.eval(logger0.info(banner))
      _ <- Resource.eval(F.delay(System.setProperty("http.agent", userAgentString)))
      userAgent <- Resource.eval(F.fromEither(`User-Agent`.parse(1)(userAgentString)))
      middleware = ClientConfiguration
        .setUserAgent[F](userAgent)
        .andThen(ClientConfiguration.retryAfter[F](maxAttempts = 5))
      defaultClient <- ClientConfiguration.build(
        ClientConfiguration.BuilderMiddleware.default,
        middleware
      )
      urlCheckerClient <- ClientConfiguration.build(
        ClientConfiguration.disableFollowRedirect,
        middleware
      )
      fileAlg0 = FileAlg.create(logger0, F)
      context <- usage match {
        case StewardUsage.Regular(config) =>
          initRegular(config)(
            defaultClient,
            UrlCheckerClient(urlCheckerClient),
            fileAlg0,
            logger0,
            F
          ).map(StewardContext.Regular(_))

        case StewardUsage.ValidateRepoConfig(file) =>
          implicit val fileAlg: FileAlg[F] = fileAlg0
          implicit val logger: Logger[F] = logger0
          Resource.pure[F, StewardContext[F]](initValidateRepoConfig(file))
      }

    } yield context

  def initRegular[F[_]](config: Config)(implicit
      client: Client[F],
      urlCheckerClient: UrlCheckerClient[F],
      fileAlg: FileAlg[F],
      logger: Logger[F],
      F: Async[F]
  ): Resource[F, Context[F]] = {
    implicit val processAlg = ProcessAlg.create(config.processCfg)
    implicit val workspaceAlg = WorkspaceAlg.create(config)
    Resource.eval(step1(config))
  }

  def initValidateRepoConfig[F[_]](file: File)(implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      F: MonadThrow[F]
  ): StewardContext.ValidateRepoConfig[F] = {
    implicit val validateRepoConfigAlg = new ValidateRepoConfigAlg[F]()
    StewardContext.ValidateRepoConfig[F](file)
  }

  def step1[F[_]](config: Config)(implicit
      client: Client[F],
      urlCheckerClient: UrlCheckerClient[F],
      fileAlg: FileAlg[F],
      logger: Logger[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Async[F]
  ): F[Context[F]] =
    for {
      forgeUser <- config.forgeUser[F]
      artifactMigrationsLoader0 = new ArtifactMigrationsLoader[F]
      artifactMigrationsFinder0 <- artifactMigrationsLoader0.createFinder(config.artifactCfg)
      scalafixMigrationsLoader0 = new ScalafixMigrationsLoader[F]
      scalafixMigrationsFinder0 <- scalafixMigrationsLoader0.createFinder(config.scalafixCfg)
      repoConfigLoader0 = new RepoConfigLoader[F]
      maybeGlobalRepoConfig <- repoConfigLoader0.loadGlobalRepoConfig(config.repoConfigCfg)
      urlChecker0 <- UrlChecker
        .create[F](config, ForgeSelection.authenticateIfApiHost(config.forgeCfg, forgeUser))
      kvsPrefix = Some(config.forgeCfg.tpe.asString)
      pullRequestsStore <- JsonKeyValueStore
        .create[F, Repo, Map[Uri, PullRequestRepository.Entry]]("pull_requests", "2", kvsPrefix)
        .flatMap(CachingKeyValueStore.wrap(_))
      refreshErrorStore <- JsonKeyValueStore
        .create[F, Repo, RefreshErrorAlg.Entry]("refresh_error", "1", kvsPrefix)
      repoCacheStore <- JsonKeyValueStore
        .create[F, Repo, RepoCache]("repo_cache", "1", kvsPrefix)
      versionsStore <- JsonKeyValueStore
        .create[F, VersionsCache.Key, VersionsCache.Value]("versions", "2")
    } yield {
      implicit val artifactMigrationsLoader: ArtifactMigrationsLoader[F] = artifactMigrationsLoader0
      implicit val artifactMigrationsFinder: ArtifactMigrationsFinder = artifactMigrationsFinder0
      implicit val scalafixMigrationsLoader: ScalafixMigrationsLoader[F] = scalafixMigrationsLoader0
      implicit val scalafixMigrationsFinder: ScalafixMigrationsFinder = scalafixMigrationsFinder0
      implicit val urlChecker: UrlChecker[F] = urlChecker0
      implicit val dateTimeAlg: DateTimeAlg[F] = DateTimeAlg.create[F]
      implicit val repoConfigAlg: RepoConfigAlg[F] = new RepoConfigAlg[F](maybeGlobalRepoConfig)
      implicit val filterAlg: FilterAlg[F] = new FilterAlg[F]
      implicit val gitAlg: GitAlg[F] = GenGitAlg.create[F](config.gitCfg)
      implicit val gitHubAuthAlg: GitHubAuthAlg[F] = GitHubAuthAlg.create[F]
      implicit val hookExecutor: HookExecutor[F] = new HookExecutor[F]
      implicit val httpJsonClient: HttpJsonClient[F] = new HttpJsonClient[F]
      implicit val repoCacheRepository: RepoCacheRepository[F] =
        new RepoCacheRepository[F](repoCacheStore)
      implicit val forgeApiAlg: ForgeApiAlg[F] =
        ForgeSelection.forgeApiAlg[F](config.forgeCfg, config.forgeSpecificCfg, forgeUser)
      implicit val forgeRepoAlg: ForgeRepoAlg[F] = new ForgeRepoAlg[F](config)
      implicit val forgeCfg: ForgeCfg = config.forgeCfg
      implicit val updateInfoUrlFinder: UpdateInfoUrlFinder[F] = new UpdateInfoUrlFinder[F]
      implicit val pullRequestRepository: PullRequestRepository[F] =
        new PullRequestRepository[F](pullRequestsStore)
      implicit val scalafixCli: ScalafixCli[F] = new ScalafixCli[F]
      implicit val scalafmtAlg: ScalafmtAlg[F] = new ScalafmtAlg[F](config)
      implicit val selfCheckAlg: SelfCheckAlg[F] = new SelfCheckAlg[F](config)
      implicit val coursierAlg: CoursierAlg[F] = CoursierAlg.create[F]
      implicit val versionsCache: VersionsCache[F] =
        new VersionsCache[F](config.cacheTtl, versionsStore)
      implicit val updateAlg: UpdateAlg[F] = new UpdateAlg[F]
      implicit val mavenAlg: MavenAlg[F] = new MavenAlg[F](config)
      implicit val sbtAlg: SbtAlg[F] = new SbtAlg[F](config)
      implicit val scalaCliAlg: ScalaCliAlg[F] = new ScalaCliAlg[F]
      implicit val millAlg: MillAlg[F] = new MillAlg[F]
      implicit val buildToolDispatcher: BuildToolDispatcher[F] = new BuildToolDispatcher[F]
      implicit val refreshErrorAlg: RefreshErrorAlg[F] =
        new RefreshErrorAlg[F](refreshErrorStore, config.refreshBackoffPeriod)
      implicit val repoCacheAlg: RepoCacheAlg[F] = new RepoCacheAlg[F](config)
      implicit val scannerAlg: ScannerAlg[F] = new ScannerAlg[F]
      implicit val editAlg: EditAlg[F] = new EditAlg[F]
      implicit val nurtureAlg: NurtureAlg[F] = new NurtureAlg[F](config.forgeCfg)
      implicit val pruningAlg: PruningAlg[F] = new PruningAlg[F]
      implicit val gitHubAppApiAlg: GitHubAppApiAlg[F] =
        new GitHubAppApiAlg[F](config.forgeCfg.apiHost)
      implicit val stewardAlg: StewardAlg[F] = new StewardAlg[F](config)
      new Context[F]
    }

  private val banner: String = {
    val banner =
      """|  ____            _         ____  _                             _
         | / ___|  ___ __ _| | __ _  / ___|| |_ _____      ____ _ _ __ __| |
         | \___ \ / __/ _` | |/ _` | \___ \| __/ _ \ \ /\ / / _` | '__/ _` |
         |  ___) | (_| (_| | | (_| |  ___) | ||  __/\ V  V / (_| | | | (_| |
         | |____/ \___\__,_|_|\__,_| |____/ \__\___| \_/\_/ \__,_|_|  \__,_|""".stripMargin
    List(" ", banner, s" v${org.scalasteward.core.BuildInfo.version}", " ")
      .mkString(System.lineSeparator())
  }

  private val userAgentString: String =
    s"Scala-Steward/${org.scalasteward.core.BuildInfo.version} (${org.scalasteward.core.BuildInfo.gitHubUrl})"
}
