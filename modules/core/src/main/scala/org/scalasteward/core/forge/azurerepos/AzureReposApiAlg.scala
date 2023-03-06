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

package org.scalasteward.core.forge.azurerepos

import cats.MonadThrow
import cats.syntax.all._
import org.http4s.{Request, Uri}
import org.scalasteward.core.application.Config.AzureReposCfg
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.azurerepos.JsonCodec._
import org.scalasteward.core.forge.data._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.log4cats.Logger

final class AzureReposApiAlg[F[_]](
    azureAPiHost: Uri,
    config: AzureReposCfg,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit client: HttpJsonClient[F], logger: Logger[F], F: MonadThrow[F])
    extends ForgeApiAlg[F] {

  private val url = new Url(azureAPiHost, config.organization.getOrElse(""))

  override def createFork(repo: Repo): F[RepoOut] =
    F.raiseError(new NotImplementedError(s"createFork($repo)"))

  // https://docs.microsoft.com/en-us/rest/api/azure/devops/git/pull-requests/create?view=azure-devops-rest-7.1
  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val create = client.postWithBody[PullRequestOut, PullRequestPayload](
      url.pullRequests(repo),
      PullRequestPayload.from(data),
      modify(repo)
    )
    for {
      _ <- F.whenA(data.assignees.nonEmpty)(warnIfAssigneesAreUsed)
      _ <- F.whenA(data.reviewers.nonEmpty)(warnIfReviewersAreUsed)
      pullRequestOut <- create
    } yield pullRequestOut
  }

  // https://docs.microsoft.com/en-us/rest/api/azure/devops/git/pull-requests/update?view=azure-devops-rest-7.1
  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    client.patchWithBody[PullRequestOut, ClosePullRequestPayload](
      url.closePullRequest(repo, number),
      ClosePullRequestPayload("abandoned"),
      modify(repo)
    )

  // https://docs.microsoft.com/en-us/rest/api/azure/devops/git/stats/get?view=azure-devops-rest-7.1
  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get[BranchOut](url.getBranch(repo, branch), modify(repo))

  // https://docs.microsoft.com/en-us/rest/api/azure/devops/git/repositories/get-repository-with-parent?view=azure-devops-rest-7.1
  override def getRepo(repo: Repo): F[RepoOut] =
    client.get[RepoOut](url.getRepo(repo), modify(repo))

  // https://docs.microsoft.com/en-us/rest/api/azure/devops/git/pull-requests/get-pull-requests?view=azure-devops-rest-7.1
  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client
      .get[Paginated[PullRequestOut]](url.listPullRequests(repo, head, base), modify(repo))
      .map(_.value)

  // https://docs.microsoft.com/en-us/rest/api/azure/devops/git/pull-request-threads/create?view=azure-devops-rest-7.1
  override def commentPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      comment: String
  ): F[Comment] =
    client.postWithBody[Comment, PullRequestCommentPayload](
      url.commentPullRequest(repo, number),
      PullRequestCommentPayload.createComment(comment),
      modify(repo)
    )

  private def warnIfAssigneesAreUsed =
    logger.warn("assignees are not supported by AzureRepos")

  private def warnIfReviewersAreUsed =
    logger.warn("reviewers are not implemented yet for AzureRepos")

}
