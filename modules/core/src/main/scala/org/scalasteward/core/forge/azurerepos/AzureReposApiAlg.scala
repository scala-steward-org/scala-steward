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
import io.circe.Json
import io.circe.syntax.KeyOps
import org.http4s.{Request, Uri}
import org.scalasteward.core.application.Config.AzureReposConfig
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.azurerepos.JsonCodec._
import org.scalasteward.core.forge.data._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient

final class AzureReposApiAlg[F[_]](
    azureAPiHost: Uri,
    config: AzureReposConfig,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit client: HttpJsonClient[F], monadErrorF: MonadThrow[F])
    extends ForgeApiAlg[F] {

  private val url = new Url(azureAPiHost, config.organization.getOrElse(""))

  override def createFork(repo: Repo): F[RepoOut] =
    monadErrorF.raiseError(new NotImplementedError(s"createFork($repo)"))

  // https://docs.microsoft.com/en-us/rest/api/azure/devops/git/pull-requests/create?view=azure-devops-rest-7.1
  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] =
    client.postWithBody[PullRequestOut, PullRequestPayload](
      url.pullRequests(repo),
      PullRequestPayload.from(data),
      modify(repo)
    )

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

  // https://docs.microsoft.com/en-us/rest/api/azure/devops/git/pull-request-labels/create?view=azure-devops-rest-7.1
  override def labelPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      labels: List[String]
  ): F[Unit] =
    client
      .postWithBody[Json, Json](
        url.labelPullRequest(repo, number),
        Json.obj("name" := labels.mkString("-")),
        modify(repo)
      )
      .void
}
