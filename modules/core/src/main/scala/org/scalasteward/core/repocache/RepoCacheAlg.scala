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
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.{Dependency, DependencyInfo, RepoData}
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.vcs.data.{Repo, RepoOut}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSRepoAlg}
import org.typelevel.log4cats.Logger
import org.scalasteward.core.git.Branch

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
  def checkCache(repo: Repo): F[(RepoData, RepoOut)] =
    logger.info(s"Check cache of ${repo.show}") >>
      refreshErrorAlg.skipIfFailedRecently(repo) {
        (
          vcsApiAlg.createForkOrGetRepoWithBranch(repo, config.doNotFork),
          repoCacheRepository.findCache(repo)
        ).parTupled.flatMap { case ((repoOut, branchOut), maybeCache) =>
          val latestSha1 = branchOut.commit.sha
          maybeCache
            .filter(_.sha1 === latestSha1)
            .fold(cloneAndRefreshCache(repo, repoOut))(supplementCache(repo, _))
            .map(data => (data, repoOut))
        }
      }

  private def supplementCache(repo: Repo, cache: RepoCache): F[RepoData] =
    repoConfigAlg.mergeWithDefault(cache.maybeRepoConfig).map { config =>
      RepoData(repo, cache, config)
    }

  private def cloneAndRefreshCache(repo: Repo, repoOut: RepoOut): F[RepoData] =
    vcsRepoAlg.cloneAndSync(repo, repoOut) >> refreshCache(repo, repoOut.default_branch)

  private def refreshCache(repo: Repo, branch: Branch): F[RepoData] =
    for {
      _ <- logger.info(s"Refresh cache of ${repo.show}")
      data <- refreshErrorAlg.persistError(repo)(computeCache(repo, branch))
      _ <- repoCacheRepository.updateCache(repo, data.cache)
    } yield data

  private def computeCache(repo: Repo, branch: Branch): F[RepoData] =
    for {
      _ <- gitAlg.checkoutBranch(repo, branch)
      branch <- gitAlg.currentBranch(repo)
      latestSha1 <- gitAlg.latestSha1(repo, branch)
      maybeConfig <- repoConfigAlg.readRepoConfig(repo)
      config <- repoConfigAlg.mergeWithDefault(maybeConfig)
      dependencies <- buildToolDispatcher.getDependencies(repo, config)
      dependencyInfos <-
        dependencies.traverse(_.traverse(_.traverse(gatherDependencyInfo(repo, _))))
      cache = RepoCache(latestSha1, dependencyInfos, maybeConfig)
    } yield RepoData(repo, cache, config)

  private def gatherDependencyInfo(repo: Repo, dependency: Dependency): F[DependencyInfo] =
    gitAlg.findFilesContaining(repo, dependency.version).map(DependencyInfo(dependency, _))
}
