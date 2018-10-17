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

package eu.timepit.scalasteward.nurture

import cats.effect.Sync
import cats.implicits._
import cats.{FlatMap, Monad}
import eu.timepit.scalasteward.application.Config
import eu.timepit.scalasteward.git.GitAlg
import eu.timepit.scalasteward.github.GitHubApiAlg
import eu.timepit.scalasteward.github.data.{PullRequestOut, Repo}
import eu.timepit.scalasteward.model.Update
import eu.timepit.scalasteward.sbt.SbtAlg
import eu.timepit.scalasteward.update.FilterAlg
import eu.timepit.scalasteward.{github, util}
import io.chrisdavenport.log4cats.Logger

class NurtureAlg[F[_]](
    config: Config,
    filterAlg: FilterAlg[F],
    gitAlg: GitAlg[F],
    gitHubApiAlg: GitHubApiAlg[F],
    logger: Logger[F],
    sbtAlg: SbtAlg[F]
) {
  def cloneAndSync(repo: Repo)(implicit F: Sync[F]): F[Unit] =
    for {
      _ <- logger.info(s"Clone and synchronize ${repo.show}")
      user <- config.gitHubUser
      repoOut <- gitHubApiAlg.createFork(repo)
      parent <- repoOut.parentOrRaise[F]
      cloneUrl = util.uri.withUserInfo(repoOut.clone_url, user)
      _ <- gitAlg.clone(repo, cloneUrl)
      _ <- gitAlg.setAuthor(repo, config.gitAuthor)
      _ <- gitAlg.syncFork(repo, parent.clone_url, parent.default_branch)
    } yield ()

  def updateDependencies(repo: Repo)(implicit F: Monad[F]): F[Unit] =
    for {
      _ <- logger.info(s"Check updates for ${repo.show}")
      updates <- sbtAlg.getUpdatesForRepo(repo)
      filtered <- filterAlg.filterMany(repo, updates)
      _ <- logger.info(util.logger.showUpdates(filtered))
      _ <- filtered.traverse_(processUpdate(repo, _))
    } yield ()

  def processUpdate(repo: Repo, update: Update)(implicit F: FlatMap[F]): F[Unit] =
    for {
      _ <- logger.info(s"Process update ${update.show}")
      head = github.headOf(config.gitHubLogin, update)
      pullRequests <- gitHubApiAlg.listPullRequests(repo, head)
      maybePullRequest = pullRequests.headOption
      _ <- maybePullRequest match {
        case Some(pullRequest) if pullRequest.state === "closed" =>
          logger.info("PR has been closed")
        case Some(pullRequest) =>
          updatePullRequest(repo, update, pullRequest)
        case None =>
          applyUpdate(repo, update)
      }
    } yield ()

  def applyUpdate(repo: Repo, update: Update): F[Unit] = ???

  def updatePullRequest(repo: Repo, update: Update, pullRequest: PullRequestOut): F[Unit] = ???
}
