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
import io.circe.Json
import org.http4s.{Request, Uri}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeApiAlg
import org.scalasteward.core.forge.data._
import org.scalasteward.core.forge.github.GitHubException._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.typelevel.log4cats.Logger

final class GitHubApiAlg[F[_]](
    gitHubApiHost: Uri,
    modify: Request[F] => F[Request[F]]
)(implicit
    client: HttpJsonClient[F],
    logger: Logger[F],
    F: MonadThrow[F]
) extends ForgeApiAlg[F] {
  private val url = new Url(gitHubApiHost)

  /** https://docs.github.com/en/rest/repos/forks?apiVersion=2022-11-28#create-a-fork */
  override def createFork(repo: Repo): F[RepoOut] =
    client.post[RepoOut](url.forks(repo), modify).flatTap { repoOut =>
      F.raiseWhen(repoOut.parent.exists(_.archived))(RepositoryArchived(repo))
    }

  /** https://docs.github.com/en/rest/pulls?apiVersion=2022-11-28#create-a-pull-request */
  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val payload = CreatePullRequestPayload.from(data)
    val create = client
      .postWithBody[PullRequestOut, CreatePullRequestPayload](
        uri = url.pulls(repo),
        body = payload,
        modify = modify
      )
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

  /** https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#update-a-pull-request */
  override def updatePullRequest(
      number: PullRequestNumber,
      repo: Repo,
      data: NewPullRequestData
  ): F[Unit] = {
    val payload = UpdatePullRequestPayload.from(data)

    val update = client
      .patchWithBody[PullRequestOut, UpdatePullRequestPayload](
        uri = url.pull(repo, number),
        body = payload,
        modify = modify
      )
      .adaptErr(SecondaryRateLimitExceeded.fromThrowable)

    for {
      _ <- update
      _ <- F.whenA(data.labels.nonEmpty)(labelPullRequest(repo, number, data.labels))
      _ <- F.whenA(data.assignees.nonEmpty)(addAssignees(repo, number, data.assignees))
      _ <- F.whenA(data.reviewers.nonEmpty)(addReviewers(repo, number, data.reviewers))
    } yield ()
  }

  /** https://docs.github.com/en/rest/repos/branches?apiVersion=2022-11-28#get-branch */
  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.branches(repo, branch), modify)

  /** https://docs.github.com/en/rest/repos?apiVersion=2022-11-28#get */
  override def getRepo(repo: Repo): F[RepoOut] =
    client.get[RepoOut](url.repos(repo), modify).flatTap { repoOut =>
      F.raiseWhen(repoOut.archived)(RepositoryArchived(repo))
    }

  /** https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#list-pull-requests */
  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client.get(url.listPullRequests(repo, head, base), modify)

  /** https://docs.github.com/en/rest/pulls?apiVersion=2022-11-28#update-a-pull-request */
  override def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut] =
    client.patchWithBody[PullRequestOut, UpdateState](
      url.pull(repo, number),
      UpdateState(PullRequestState.Closed),
      modify
    )

  /** https://docs.github.com/en/rest/issues?apiVersion=2022-11-28#create-an-issue-comment */
  override def commentPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      comment: String
  ): F[Comment] =
    client
      .postWithBody(url.comments(repo, number), Comment(comment), modify)

  /** https://docs.github.com/en/rest/reference/issues?apiVersion=2022-11-28#add-labels-to-an-issue
    */
  private def labelPullRequest(
      repo: Repo,
      number: PullRequestNumber,
      labels: List[String]
  ): F[Unit] =
    client
      .postWithBody[io.circe.Json, GitHubLabels](
        url.issueLabels(repo, number),
        GitHubLabels(labels),
        modify
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
        modify
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
        modify
      )
      .void
      .handleErrorWith { error =>
        logger.error(error)(
          s"cannot request review from '${reviewers.mkString(",")}' for PR '$number'"
        )
      }

}
