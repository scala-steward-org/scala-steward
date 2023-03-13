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

package org.scalasteward.core.forge.github

import cats.MonadThrow
import cats.syntax.all._
import org.http4s.{Request, Uri}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.data._
import org.scalasteward.core.forge.github.GitHubException._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.log4cats.Logger
import io.circe.Json

final class GitHubApiAlg[F[_]](
    gitHubApiHost: Uri,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit
    client: HttpJsonClient[F],
    logger: Logger[F],
    F: MonadThrow[F]
) extends ForgeApiAlg[F] {
  private val url = new Url(gitHubApiHost)

  /** https://developer.github.com/v3/repos/forks/#create-a-fork */
  override def createFork(repo: Repo): F[RepoOut] =
    client.post[RepoOut](url.forks(repo), modify(repo)).flatTap { repoOut =>
      F.raiseWhen(repoOut.parent.exists(_.archived))(RepositoryArchived(repo))
    }

  /** https://developer.github.com/v3/pulls/#create-a-pull-request */
  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val payload = PullRequestPayload.from(data)
    val create = client
      .postWithBody[PullRequestOut, PullRequestPayload](url.pulls(repo), payload, modify(repo))
      .adaptErr(SecondaryRateLimitExceeded.fromThrowable)

    for {
      pullRequestOut <- create
      _ <- F.whenA(data.labels.nonEmpty)(labelPullRequest(repo, pullRequestOut.number, data.labels))
      _ <-
        F.whenA(data.assignees.nonEmpty)(addAssignees(repo, pullRequestOut.number, data.assignees))
      _ <-
        F.whenA(data.reviewers.nonEmpty)(addReviewers(repo, pullRequestOut.number, data.reviewers))
    } yield pullRequestOut
  }

  /** https://developer.github.com/v3/repos/branches/#get-branch */
  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.branches(repo, branch), modify(repo))

  /** https://developer.github.com/v3/repos/#get */
  override def getRepo(repo: Repo): F[RepoOut] =
    client.get[RepoOut](url.repos(repo), modify(repo)).flatTap { repoOut =>
      F.raiseWhen(repoOut.archived)(RepositoryArchived(repo))
    }

  /** https://developer.github.com/v3/pulls/#list-pull-requests */
  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client.get(url.listPullRequests(repo, head, base), modify(repo))

  /** https://developer.github.com/v3/pulls/#update-a-pull-request */
  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    client.patchWithBody[PullRequestOut, UpdateState](
      url.pull(repo, number),
      UpdateState(PullRequestState.Closed),
      modify(repo)
    )

  /** https://developer.github.com/v3/issues#create-an-issue-comment */
  override def commentPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      comment: String
  ): F[Comment] =
    client
      .postWithBody(url.comments(repo, number), Comment(comment), modify(repo))

  /** https://docs.github.com/en/rest/reference/issues#add-labels-to-an-issue */
  private def labelPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      labels: List[String]
  ): F[Unit] =
    client
      .postWithBody[io.circe.Json, GitHubLabels](
        url.issueLabels(repo, number),
        GitHubLabels(labels),
        modify(repo)
      )
      .adaptErr(SecondaryRateLimitExceeded.fromThrowable)
      .void

  private def addAssignees(
      repo: Repo,
      number: PullRequestNumber,
      assignees: List[String]
  ): F[Unit] =
    client
      .postWithBody[Json, GitHubAssignees](
        url.assignees(repo, number),
        GitHubAssignees(assignees),
        modify(repo)
      )
      .void
      .handleErrorWith { error =>
        logger.error(error)(s"cannot add assignees '${assignees.mkString(",")}' to PR '$number'")
      }

  private def addReviewers(
      repo: Repo,
      number: PullRequestNumber,
      reviewers: List[String]
  ): F[Unit] =
    client
      .postWithBody[Json, GitHubReviewers](
        url.reviewers(repo, number),
        GitHubReviewers(reviewers),
        modify(repo)
      )
      .void
      .handleErrorWith { error =>
        logger.error(error)(
          s"cannot request review from '${reviewers.mkString(",")}' for PR '$number'"
        )
      }

}
