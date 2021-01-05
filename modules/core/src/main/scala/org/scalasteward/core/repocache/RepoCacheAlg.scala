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

package org.scalasteward.core.repocache

import cats.syntax.all._
import cats.{MonadThrow, Parallel}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.{Dependency, DependencyInfo}
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.vcs.data.{Repo, RepoOut}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSRepoAlg}

final class RepoCacheAlg[F[_]](config: Config)(implicit
    buildToolDispatcher: BuildToolDispatcher[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    parallel: Parallel[F],
    refreshErrorAlg: RefreshErrorAlg[F],
    repoCacheRepository: RepoCacheRepository[F],
    repoConfigAlg: RepoConfigAlg[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    F: MonadThrow[F]
) {
  def checkCache(repo: Repo): F[(RepoCache, RepoOut)] =
    logger.info(s"Check cache of ${repo.show}") >>
      refreshErrorAlg.skipIfFailedRecently(repo) {
        for {
          ((repoOut, branchOut), maybeCache) <- (
            vcsApiAlg.createForkOrGetRepoWithDefaultBranch(repo, config.doNotFork),
            repoCacheRepository.findCache(repo)
          ).parTupled
          latestSha1 = branchOut.commit.sha
          cache <- maybeCache
            .filter(_.sha1 === latestSha1)
            .fold(cloneAndRefreshCache(repo, repoOut))(F.pure)
        } yield (cache, repoOut)
      }

  private def cloneAndRefreshCache(repo: Repo, repoOut: RepoOut): F[RepoCache] =
    vcsRepoAlg.cloneAndSync(repo, repoOut) >> refreshCache(repo)

  private def refreshCache(repo: Repo): F[RepoCache] =
    for {
      _ <- logger.info(s"Refresh cache of ${repo.show}")
      cache <- refreshErrorAlg.persistError(repo)(computeCache(repo))
      _ <- repoCacheRepository.updateCache(repo, cache)
    } yield cache

  private def computeCache(repo: Repo): F[RepoCache] =
    for {
      branch <- gitAlg.currentBranch(repo)
      latestSha1 <- gitAlg.latestSha1(repo, branch)
      dependencies <- buildToolDispatcher.getDependencies(repo)
      dependencyInfos <-
        dependencies
          .traverse(_.traverse(_.traverse(gatherDependencyInfo(repo, _))))
      maybeRepoConfig <- repoConfigAlg.readRepoConfig(repo)
    } yield RepoCache(latestSha1, dependencyInfos, maybeRepoConfig)

  private def gatherDependencyInfo(repo: Repo, dependency: Dependency): F[DependencyInfo] =
    gitAlg.findFilesContaining(repo, dependency.version).map(DependencyInfo(dependency, _))
}
