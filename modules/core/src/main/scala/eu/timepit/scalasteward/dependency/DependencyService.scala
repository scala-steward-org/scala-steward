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

import cats.MonadError
import cats.implicits._
import eu.timepit.scalasteward.git.GitService
import eu.timepit.scalasteward.github.GitHubService
import eu.timepit.scalasteward.github.data.{AuthenticatedUser, Repo}
import eu.timepit.scalasteward.github.http4s.http4sUrl

class DependencyService[F[_]](
    dependencyRepository: DependencyRepository[F],
    gitHubService: GitHubService[F],
    gitService: GitService[F]
) {
  def refreshDependenciesIfNecessary(
      user: AuthenticatedUser,
      repo: Repo
  )(implicit F: MonadError[F, Throwable]): F[Unit] =
    for {
      res <- gitHubService.createForkAndGetDefaultBranch(user, repo)
      (repoOut, branchOut) = res
      foundSha1 <- dependencyRepository.findSha1(repo)
      refreshRequired = foundSha1.fold(true)(_ =!= branchOut.commit.sha)
      _ <- if (refreshRequired) refreshDependencies(user, repoOut.clone_url) else F.unit
    } yield ()

  def refreshDependencies(user: AuthenticatedUser, cloneUrl: String)(
      implicit F: MonadError[F, Throwable]
  ): F[Unit] = {
    for {
      _ <- http4sUrl.fromString[F](cloneUrl).map(http4sUrl.withUserInfo(_, user))
      _ <- gitService.clone(cloneUrl, null)
    } yield ()
    // git clone repo
    // sync own fork with upstream
    // parse sbt libraryDependenciesAsJson
    // dependencyRepository.setDependencies
    // remove clone
    locally(())
    ???
  }
}
