/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.core.forge

import cats.effect.Temporal
import cats.syntax.all._
import cats.{ApplicativeThrow, MonadThrow, Parallel}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.Forge.{
  AzureRepos,
  Bitbucket,
  BitbucketServer,
  GitHub,
  GitLab,
  Gitea
}
import org.scalasteward.core.forge.azurerepos.AzureReposApiAlg
import org.scalasteward.core.forge.bitbucket.BitbucketApiAlg
import org.scalasteward.core.forge.bitbucketserver.BitbucketServerApiAlg
import org.scalasteward.core.forge.data._
import org.scalasteward.core.forge.gitea.GiteaApiAlg
import org.scalasteward.core.forge.github.GitHubApiAlg
import org.scalasteward.core.forge.gitlab.GitLabApiAlg
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.log4cats.Logger

trait ForgeApiAlg[F[_]] {
  def createFork(repo: Repo): F[RepoOut]

  def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut]

  def updatePullRequest(number: PullRequestNumber, repo: Repo, data: NewPullRequestData): F[Unit]

  def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut]

  def getBranch(repo: Repo, branch: Branch): F[BranchOut]

  def getRepo(repo: Repo): F[RepoOut]

  def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]]

  def referencePullRequest(number: PullRequestNumber): String =
    s"#${number.value}"

  def commentPullRequest(repo: Repo, number: PullRequestNumber, comment: String): F[Comment]

  final def createForkOrGetRepo(repo: Repo, doNotFork: Boolean): F[RepoOut] =
    if (doNotFork) getRepo(repo) else createFork(repo)

  final def createForkOrGetRepoWithBranch(repo: Repo, doNotFork: Boolean)(implicit
      F: MonadThrow[F]
  ): F[(RepoOut, BranchOut)] =
    for {
      forkOrRepo <- createForkOrGetRepo(repo, doNotFork)
      forkOrRepoWithDefaultBranch = repo.branch.fold(forkOrRepo)(forkOrRepo.withBranch)
      defaultBranch <- getDefaultBranchOfParentOrRepo(forkOrRepoWithDefaultBranch, doNotFork)
    } yield (forkOrRepoWithDefaultBranch, defaultBranch)

  final def getDefaultBranchOfParentOrRepo(repoOut: RepoOut, doNotFork: Boolean)(implicit
      F: MonadThrow[F]
  ): F[BranchOut] =
    parentOrRepo(repoOut, doNotFork).flatMap(getDefaultBranch)

  final def parentOrRepo(repoOut: RepoOut, doNotFork: Boolean)(implicit
      F: ApplicativeThrow[F]
  ): F[RepoOut] =
    if (doNotFork) F.pure(repoOut) else repoOut.parentOrRaise[F]

  private def getDefaultBranch(repoOut: RepoOut): F[BranchOut] =
    getBranch(repoOut.repo, repoOut.default_branch)
}

object ForgeApiAlg {
  def create[F[_]: Parallel](forge: Forge)(implicit
      httpJsonClient: HttpJsonClient[F],
      forgeAuthAlg: ForgeAuthAlg[F],
      logger: Logger[F],
      F: Temporal[F]
  ): ForgeApiAlg[F] = {
    val auth = forgeAuthAlg.authenticateApi(_)
    forge match {
      case forge: AzureRepos      => new AzureReposApiAlg(forge, auth)
      case forge: Bitbucket       => new BitbucketApiAlg(forge, auth)
      case forge: BitbucketServer => new BitbucketServerApiAlg(forge, auth)
      case forge: GitHub          => new GitHubApiAlg(forge, auth)
      case forge: GitLab          => new GitLabApiAlg(forge, auth)
      case forge: Gitea           => new GiteaApiAlg(forge, auth)
    }
  }
}
