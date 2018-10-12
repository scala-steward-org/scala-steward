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

package eu.timepit.scalasteward

import cats.effect.IO
import cats.implicits._
import eu.timepit.scalasteward.application.Config
import eu.timepit.scalasteward.github._
import eu.timepit.scalasteward.github.data.{CreatePullRequestIn, PullRequestOut}
import eu.timepit.scalasteward.model._

object githubLegacy {

  def createPullRequest(
      localUpdate: LocalUpdate,
      gitHubService: GitHubService[IO],
      config: Config
  ): IO[PullRequestOut] =
    config.gitHubUser[IO].flatMap { user =>
      val in = CreatePullRequestIn(
        title = localUpdate.commitMsg,
        body = CreatePullRequestIn.bodyOf(localUpdate.update, config.gitHubLogin),
        head = s"${config.gitHubLogin}:${localUpdate.updateBranch.name}",
        base = localUpdate.localRepo.base
      )
      gitHubService.createPullRequest(user, localUpdate.localRepo.upstream, in)
    }

  def createPullRequestIfNotExists(
      localUpdate: LocalUpdate,
      gitHubService: GitHubService[IO],
      config: Config
  ): IO[Unit] =
    pullRequestExists(localUpdate, gitHubService, config).ifM(
      log.printInfo(s"PR ${localUpdate.updateBranch.name} already exists"),
      log.printInfo(s"Create PR ${localUpdate.updateBranch.name}") >>
        createPullRequest(localUpdate, gitHubService, config).void
    )

  def headOf(localUpdate: LocalUpdate, config: Config): String =
    s"${config.gitHubLogin}:${localUpdate.updateBranch.name}"

  def pullRequestExists(
      localUpdate: LocalUpdate,
      gitHubService: GitHubService[IO],
      config: Config
  ): IO[Boolean] = {
    val repo = localUpdate.localRepo.upstream
    for {
      user <- config.gitHubUser[IO]
      pullRequests <- gitHubService.listPullRequests(user, repo, headOf(localUpdate, config))
    } yield pullRequests.nonEmpty
  }
}
