/*
 * Copyright 2018-2021 Scala Steward contributors
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

import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.okhttp.client.OkHttpBuilder
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.coursier.{CoursierAlg, VersionsCache}
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.edit.hooks.HookExecutor
import org.scalasteward.core.edit.scalafix.{ScalafixMigrationsFinder, ScalafixMigrationsLoader}
import org.scalasteward.core.git.{GenGitAlg, GitAlg}
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.{NurtureAlg, PullRequestData, PullRequestRepository}
import org.scalasteward.core.persistence.{CachingKeyValueStore, JsonKeyValueStore}
import org.scalasteward.core.repocache._
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.update.{ArtifactMigrations, FilterAlg, PruningAlg, UpdateAlg}
import org.scalasteward.core.util._
import org.scalasteward.core.util.uri._
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.vcs.github.{GitHubAppApiAlg, GitHubAuthAlg}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSExtraAlg, VCSRepoAlg, VCSSelection}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final class Context[F[_]](implicit
    val buildToolDispatcher: BuildToolDispatcher[F],
    val coursierAlg: CoursierAlg[F],
    val dateTimeAlg: DateTimeAlg[F],
    val editAlg: EditAlg[F],
    val fileAlg: FileAlg[F],
    val filterAlg: FilterAlg[F],
    val gitAlg: GitAlg[F],
    val hookExecutor: HookExecutor[F],
    val logger: Logger[F],
    val mavenAlg: MavenAlg[F],
    val millAlg: MillAlg[F],
    val pruningAlg: PruningAlg[F],
    val pullRequestRepository: PullRequestRepository[F],
    val repoConfigAlg: RepoConfigAlg[F],
    val sbtAlg: SbtAlg[F],
    val scalafixMigrationsFinder: ScalafixMigrationsFinder,
    val scalafixMigrationsLoader: ScalafixMigrationsLoader[F],
    val scalafmtAlg: ScalafmtAlg[F],
    val stewardAlg: StewardAlg[F],
    val updateAlg: UpdateAlg[F],
    val vcsRepoAlg: VCSRepoAlg[F],
    val workspaceAlg: WorkspaceAlg[F]
)

object Context {
  def step0[F[_]](args: Cli.Args)(implicit F: Async[F]): Resource[F, Context[F]] =
    for {
      _ <- Resource.unit[F]
      config = Config.from(args)
      logger <- Resource.eval(Slf4jLogger.fromName[F](Context.getClass.getSimpleName))
      client <- OkHttpBuilder.withDefaultClient[F].flatMap(_.resource)
      fileAlg = FileAlg.create[F](logger, F)
      processAlg = ProcessAlg.create[F](config.processCfg)(logger, F)
      workspaceAlg = WorkspaceAlg.create[F](config)(fileAlg, logger, F)
      context <-
        Resource.eval(step1[F](config)(client, fileAlg, logger, processAlg, workspaceAlg, F))
    } yield context

  def step1[F[_]](config: Config)(implicit
      client: Client[F],
      fileAlg: FileAlg[F],
      logger: Logger[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Async[F]
  ): F[Context[F]] =
    for {
      _ <- printBanner[F]
      vcsUser <- config.vcsUser[F]
      artifactMigrations0 <- ArtifactMigrations.create[F](config)
      scalafixMigrationsLoader0 = new ScalafixMigrationsLoader[F]
      scalafixMigrationsFinder0 <- scalafixMigrationsLoader0.createFinder(config.scalafixCfg)
      urlChecker0 <- UrlChecker.create[F](config)
      kvsPrefix = Some(config.vcsType.asString)
      pullRequestsStore <- JsonKeyValueStore
        .create[F, Repo, Map[Uri, PullRequestData]]("pull_requests", "2", kvsPrefix)
        .flatMap(CachingKeyValueStore.wrap(_))
      refreshErrorStore <- JsonKeyValueStore
        .create[F, Repo, RefreshErrorAlg.Entry]("refresh_error", "1", kvsPrefix)
      repoCacheStore <- JsonKeyValueStore
        .create[F, Repo, RepoCache]("repo_cache", "1", kvsPrefix)
      versionsStore <- JsonKeyValueStore
        .create[F, VersionsCache.Key, VersionsCache.Value]("versions", "2")
    } yield {
      implicit val artifactMigrations: ArtifactMigrations = artifactMigrations0
      implicit val scalafixMigrationsLoader: ScalafixMigrationsLoader[F] = scalafixMigrationsLoader0
      implicit val scalafixMigrationsFinder: ScalafixMigrationsFinder = scalafixMigrationsFinder0
      implicit val urlChecker: UrlChecker[F] = urlChecker0
      implicit val dateTimeAlg: DateTimeAlg[F] = DateTimeAlg.create[F]
      implicit val repoConfigAlg: RepoConfigAlg[F] = new RepoConfigAlg[F](config)
      implicit val filterAlg: FilterAlg[F] = new FilterAlg[F]
      implicit val gitAlg: GitAlg[F] = GenGitAlg.create[F](config.gitCfg)
      implicit val gitHubAuthAlg: GitHubAuthAlg[F] = GitHubAuthAlg.create[F]
      implicit val hookExecutor: HookExecutor[F] = new HookExecutor[F]
      implicit val httpJsonClient: HttpJsonClient[F] = new HttpJsonClient[F]
      implicit val repoCacheRepository: RepoCacheRepository[F] =
        new RepoCacheRepository[F](repoCacheStore)
      implicit val vcsApiAlg: VCSApiAlg[F] = new VCSSelection[F](config, vcsUser).vcsApiAlg
      implicit val vcsRepoAlg: VCSRepoAlg[F] = new VCSRepoAlg[F](config)
      implicit val vcsExtraAlg: VCSExtraAlg[F] = VCSExtraAlg.create[F](config)
      implicit val pullRequestRepository: PullRequestRepository[F] =
        new PullRequestRepository[F](pullRequestsStore)
      implicit val scalafmtAlg: ScalafmtAlg[F] = new ScalafmtAlg[F](config)
      implicit val selfCheckAlg: SelfCheckAlg[F] = new SelfCheckAlg[F](config)
      implicit val coursierAlg: CoursierAlg[F] = CoursierAlg.create[F]
      implicit val versionsCache: VersionsCache[F] =
        new VersionsCache[F](config.cacheTtl, versionsStore)
      implicit val updateAlg: UpdateAlg[F] = new UpdateAlg[F]
      implicit val mavenAlg: MavenAlg[F] = MavenAlg.create[F](config)
      implicit val sbtAlg: SbtAlg[F] = SbtAlg.create[F](config)
      implicit val millAlg: MillAlg[F] = MillAlg.create[F]
      implicit val buildToolDispatcher: BuildToolDispatcher[F] = new BuildToolDispatcher[F]
      implicit val refreshErrorAlg: RefreshErrorAlg[F] = new RefreshErrorAlg[F](refreshErrorStore)
      implicit val repoCacheAlg: RepoCacheAlg[F] = new RepoCacheAlg[F](config)
      implicit val editAlg: EditAlg[F] = new EditAlg[F]
      implicit val nurtureAlg: NurtureAlg[F] = new NurtureAlg[F](config)
      implicit val pruningAlg: PruningAlg[F] = new PruningAlg[F]
      implicit val gitHubAppApiAlg: GitHubAppApiAlg[F] = new GitHubAppApiAlg[F](config.vcsApiHost)
      implicit val stewardAlg: StewardAlg[F] = new StewardAlg[F](config)
      new Context[F]
    }

  private def printBanner[F[_]](implicit logger: Logger[F]): F[Unit] = {
    val banner =
      """|  ____            _         ____  _                             _
         | / ___|  ___ __ _| | __ _  / ___|| |_ _____      ____ _ _ __ __| |
         | \___ \ / __/ _` | |/ _` | \___ \| __/ _ \ \ /\ / / _` | '__/ _` |
         |  ___) | (_| (_| | | (_| |  ___) | ||  __/\ V  V / (_| | | | (_| |
         | |____/ \___\__,_|_|\__,_| |____/ \__\___| \_/\_/ \__,_|_|  \__,_|""".stripMargin
    val msg = List(" ", banner, s" v${org.scalasteward.core.BuildInfo.version}", " ")
      .mkString(System.lineSeparator())
    logger.info(msg)
  }
}
