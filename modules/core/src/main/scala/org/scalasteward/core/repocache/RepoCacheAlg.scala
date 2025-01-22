/*
 * Copyright 2018-2025 Scala Steward contributors
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

import cats.syntax.all.*
import cats.{MonadThrow, Parallel}
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.{Dependency, DependencyInfo, Repo, RepoData}
import org.scalasteward.core.forge.data.RepoOut
import org.scalasteward.core.forge.{ForgeApiAlg, ForgeRepoAlg}
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.typelevel.log4cats.Logger

final class RepoCacheAlg[F[_]](config: Config)(implicit
    buildToolDispatcher: BuildToolDispatcher[F],
    forgeApiAlg: ForgeApiAlg[F],
    forgeRepoAlg: ForgeRepoAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    parallel: Parallel[F],
    refreshErrorAlg: RefreshErrorAlg[F],
    repoCacheRepository: RepoCacheRepository[F],
    repoConfigAlg: RepoConfigAlg[F],
    F: MonadThrow[F]
) {
  def checkCache(repo: Repo): F[(RepoData, RepoOut)] =
    for {
      _ <- logger.info(s"Check cache of ${repo.show}")
      _ <- refreshErrorAlg.throwIfFailedRecently(repo)
      getRepoAndBranch = forgeApiAlg.createForkOrGetRepoWithBranch(repo, config.forgeCfg.doNotFork)
      tuple <- (getRepoAndBranch, repoCacheRepository.findCache(repo)).parTupled
      ((repoOut, branchOut), maybeCache) = tuple
      latestSha1 = branchOut.commit.sha
      data <- maybeCache
        .filter(_.sha1 === latestSha1)
        .fold(cloneAndRefreshCache(repo, repoOut))(supplementCache(repo, _).pure[F])
    } yield (data, repoOut)

  private def supplementCache(repo: Repo, cache: RepoCache): RepoData =
    RepoData(repo, cache, repoConfigAlg.mergeWithGlobal(cache.maybeRepoConfig))

  private def cloneAndRefreshCache(repo: Repo, repoOut: RepoOut): F[RepoData] =
    forgeRepoAlg.cloneAndSync(repo, repoOut) >> refreshCache(repo)

  private def refreshCache(repo: Repo): F[RepoData] =
    for {
      _ <- logger.info(s"Refresh cache of ${repo.show}")
      data <- refreshErrorAlg.persistError(repo)(computeCache(repo))
      _ <- repoCacheRepository.updateCache(repo, data.cache)
    } yield data

  private def computeCache(repo: Repo): F[RepoData] =
    for {
      branch <- gitAlg.currentBranch(repo)
      latestSha1 <- gitAlg.latestSha1(repo, branch)
      configParsingResult <- repoConfigAlg.readRepoConfig(repo)
      maybeConfig = configParsingResult.maybeRepoConfig
      maybeConfigParsingError = configParsingResult.maybeParsingError.map(_.getMessage)
      config = repoConfigAlg.mergeWithGlobal(maybeConfig)
      dependencies <- buildToolDispatcher.getDependencies(repo, config)
      dependencyInfos <-
        dependencies.traverse(_.traverse(_.traverse(gatherDependencyInfo(repo, _))))
      _ <- gitAlg.discardChanges(repo)
      cache = RepoCache(latestSha1, dependencyInfos, maybeConfig, maybeConfigParsingError)
    } yield RepoData(repo, cache, config)

  private def gatherDependencyInfo(repo: Repo, dependency: Dependency): F[DependencyInfo] =
    gitAlg.findFilesContaining(repo, dependency.version.value).map(DependencyInfo(dependency, _))
}
