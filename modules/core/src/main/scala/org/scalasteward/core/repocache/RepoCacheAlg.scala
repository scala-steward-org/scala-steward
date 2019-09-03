/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.repocache

import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.WorkspaceAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.util.{LogAlg, MonadThrowable}
import org.scalasteward.core.vcs.data.{Repo, RepoOut}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSRepoAlg}

final class RepoCacheAlg[F[_]](
    implicit
    config: Config,
    gitAlg: GitAlg[F],
    logAlg: LogAlg[F],
    logger: Logger[F],
    repoCacheRepository: RepoCacheRepository[F],
    repoConfigAlg: RepoConfigAlg[F],
    sbtAlg: SbtAlg[F],
    scalafmtAlg: ScalafmtAlg[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadThrowable[F]
) {

  def checkCache(repo: Repo): F[Unit] =
    logAlg.attemptLog_(s"Check cache of ${repo.show}") {
      for {
        (repoOut, branchOut) <- vcsApiAlg.createForkOrGetRepoWithDefaultBranch(config, repo)
        cachedSha1 <- repoCacheRepository.findSha1(repo)
        latestSha1 = branchOut.commit.sha
        refreshRequired = cachedSha1.forall(_ =!= latestSha1)
        _ <- if (refreshRequired) cloneAndRefreshCache(repo, repoOut) else F.unit
      } yield ()
    }

  private def cloneAndRefreshCache(repo: Repo, repoOut: RepoOut): F[Unit] =
    for {
      _ <- logger.info(s"Refresh cache of ${repo.show}")
      _ <- vcsRepoAlg.clone(repo, repoOut)
      _ <- vcsRepoAlg.syncFork(repo, repoOut)
      _ <- refreshCache(repo)
      _ <- gitAlg.removeClone(repo)
    } yield ()

  private def refreshCache(repo: Repo): F[RepoCache] =
    for {
      branch <- gitAlg.currentBranch(repo)
      latestSha1 <- gitAlg.latestSha1(repo, branch)
      dependencies <- sbtAlg.getDependencies(repo)
      subProjects <- workspaceAlg.findSubProjectDirs(repo)
      sbtVersions <- subProjects.traverse(sbtAlg.getSbtVersion)
      scalafmtVersions <- subProjects.traverse(scalafmtAlg.getScalafmtVersion)
      maybeSbtVersion = subProjects.map(_.pathAsString).zip(sbtVersions).toMap.mapFilter(identity)
      maybeScalafmtVersion = subProjects
        .map(_.pathAsString)
        .zip(scalafmtVersions)
        .toMap
        .mapFilter(identity)
      maybeRepoConfig <- repoConfigAlg.readRepoConfig(repo)
      cache = RepoCache(
        latestSha1,
        dependencies,
        maybeSbtVersion,
        maybeScalafmtVersion,
        maybeRepoConfig
      )
      _ <- repoCacheRepository.updateCache(repo, cache)
    } yield cache
}
