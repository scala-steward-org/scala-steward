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

package org.scalasteward.core.github

import cats.implicits._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.github.data._
import org.scalasteward.core.util.MonadThrowable
import org.scalasteward.core.application.Config

trait GitHubApiAlg[F[_]] {

  /** https://developer.github.com/v3/repos/forks/#create-a-fork */
  def createFork(
      repo: Repo
  ): F[RepoOut]

  /** https://developer.github.com/v3/pulls/#create-a-pull-request */
  def createPullRequest(
      repo: Repo,
      data: NewPullRequestData
  ): F[PullRequestOut]

  /** https://developer.github.com/v3/repos/branches/#get-branch */
  def getBranch(
      repo: Repo,
      branch: Branch
  ): F[BranchOut]

  /** https://developer.github.com/v3/pulls/#list-pull-requests */
  def listPullRequests(
      repo: Repo,
      head: String
  ): F[List[PullRequestOut]]

  def createForkAndGetDefaultBranch(
      config: Config,
      repo: Repo
  )(implicit F: MonadThrowable[F]): F[(RepoOut, BranchOut)] =
    for {
      fork <- createFork(repo)
      parent <- fork.parentOrRaise[F](config)
      branchOut <- getDefaultBranch(parent)
    } yield (fork, branchOut)

  def getRepoInfo(repo: Repo): F[RepoOut]

  def createOrGetRepoInfo(config: Config, repo: Repo): F[RepoOut] =
    if (config.doNotFork) getRepoInfo(repo)
    else createFork(repo)

  def createOrGetRepoInfoWithBranchInfo(
      config: Config,
      repo: Repo
  )(implicit F: MonadThrowable[F]): F[(RepoOut, BranchOut)] =
    if (config.doNotFork)
      for {
        repoOut <- getRepoInfo(repo)
        branchOut <- getDefaultBranch(repoOut)
      } yield repoOut -> branchOut
    else
      createForkAndGetDefaultBranch(config, repo)

  def getDefaultBranch(
      repoOut: RepoOut
  ): F[BranchOut] =
    getBranch(repoOut.repo, repoOut.default_branch)
}
