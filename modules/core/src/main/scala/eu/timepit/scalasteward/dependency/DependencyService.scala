/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.dependency

import cats.implicits._
import eu.timepit.scalasteward.git.{GitAlg, Sha1}
import eu.timepit.scalasteward.github.GitHubApiAlg
import eu.timepit.scalasteward.github.data.{AuthenticatedUser, Repo, RepoOut}
import eu.timepit.scalasteward.sbt.SbtAlg
import eu.timepit.scalasteward.util
import eu.timepit.scalasteward.util.MonadThrowable
import eu.timepit.scalasteward.util.logger.LoggerOps
import io.chrisdavenport.log4cats.Logger

class DependencyService[F[_]](
    implicit
    dependencyRepository: DependencyRepository[F],
    gitHubApiAlg: GitHubApiAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    sbtAlg: SbtAlg[F],
    user: AuthenticatedUser
) {
  def forkAndCheckDependencies(repo: Repo)(implicit F: MonadThrowable[F]): F[Unit] =
    logger.attemptLog_(s"Fork and check dependencies of ${repo.show}") {
      for {
        res <- gitHubApiAlg.createForkAndGetDefaultBranch(repo)
        (repoOut, branchOut) = res
        foundSha1 <- dependencyRepository.findSha1(repo)
        latestSha1 = branchOut.commit.sha
        refreshRequired = foundSha1.fold(true)(_ =!= latestSha1)
        _ <- {
          if (refreshRequired) refreshDependencies(repo, repoOut, latestSha1)
          else F.unit
        }
      } yield ()
    }

  def refreshDependencies(repo: Repo, repoOut: RepoOut, latestSha1: Sha1)(
      implicit F: MonadThrowable[F]
  ): F[Unit] =
    for {
      _ <- logger.info(s"Refresh dependencies of ${repo.show}")
      cloneUrl = util.uri.withUserInfo(repoOut.clone_url, user)
      _ <- gitAlg.clone(repo, cloneUrl)
      parent <- repoOut.parentOrRaise[F]
      parentCloneUrl = util.uri.withUserInfo(parent.clone_url, user)
      _ <- gitAlg.syncFork(repo, parentCloneUrl, parent.default_branch)
      dependencies <- sbtAlg.getDependencies(repo)
      _ <- dependencyRepository.setDependencies(repo, latestSha1, dependencies)
      _ <- gitAlg.removeClone(repo)
    } yield ()
}
