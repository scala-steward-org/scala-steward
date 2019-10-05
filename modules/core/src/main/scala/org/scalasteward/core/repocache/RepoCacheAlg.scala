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
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.util.MonadThrowable
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.vcs.data.{Repo, RepoOut}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSRepoAlg}

final class RepoCacheAlg[F[_]](
    implicit
    config: Config,
    gitAlg: GitAlg[F],
    logger: Logger[F],
    refreshErrorAlg: RefreshErrorAlg[F],
    repoCacheRepository: RepoCacheRepository[F],
    repoConfigAlg: RepoConfigAlg[F],
    sbtAlg: SbtAlg[F],
    scalafmtAlg: ScalafmtAlg[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    F: MonadThrowable[F]
) {

  def checkCache(repo: Repo): F[Unit] =
    logger.attemptLog_(s"Check cache of ${repo.show}") {
      F.ifM(refreshErrorAlg.failedRecently(repo))(
        F.unit,
        for {
          (repoOut, branchOut) <- vcsApiAlg.createForkOrGetRepoWithDefaultBranch(config, repo)
          cachedSha1 <- repoCacheRepository.findSha1(repo)
          latestSha1 = branchOut.commit.sha
          refreshRequired = cachedSha1.forall(_ =!= latestSha1)
          _ <- if (refreshRequired) cloneAndRefreshCache(repo, repoOut) else F.unit
        } yield ()
      )
    }

  private def cloneAndRefreshCache(repo: Repo, repoOut: RepoOut): F[Unit] =
    for {
      _ <- logger.info(s"Refresh cache of ${repo.show}")
      _ <- vcsRepoAlg.clone(repo, repoOut)
      _ <- vcsRepoAlg.syncFork(repo, repoOut)
      _ <- refreshCache(repo)
      _ <- gitAlg.removeClone(repo)
    } yield ()

  private def refreshCache(repo: Repo): F[Unit] =
    computeCache(repo).attempt.flatMap {
      case Right(cache) =>
        repoCacheRepository.updateCache(repo, cache)
      case Left(throwable) =>
        refreshErrorAlg.persistError(repo, throwable) >> F.raiseError(throwable)
    }

  private def computeCache(repo: Repo): F[RepoCache] =
    for {
      branch <- gitAlg.currentBranch(repo)
      latestSha1 <- gitAlg.latestSha1(repo, branch)
      dependencies <- sbtAlg.getDependencies(repo)
      maybeSbtVersion <- sbtAlg.getSbtVersion(repo)
      maybeScalafmtVersion <- scalafmtAlg.getScalafmtVersion(repo)
      maybeRepoConfig <- repoConfigAlg.readRepoConfig(repo)
    } yield RepoCache(
      latestSha1,
      dependencies,
      maybeSbtVersion,
      maybeScalafmtVersion,
      maybeRepoConfig
    )
}
