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

package org.scalasteward.core.vcs.github

import cats.MonadThrow
import cats.syntax.all._
import org.http4s.{Request, Uri}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.vcs.github.GitHubException._

final class GitHubApiAlg[F[_]](
    gitHubApiHost: Uri,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit
    client: HttpJsonClient[F],
    F: MonadThrow[F]
) extends VCSApiAlg[F] {
  private val url = new Url(gitHubApiHost)

  /** https://developer.github.com/v3/repos/forks/#create-a-fork */
  override def createFork(repo: Repo): F[RepoOut] =
    client.post[RepoOut](url.forks(repo), modify(repo)).flatTap { repoOut =>
      F.raiseWhen(repoOut.parent.exists(_.archived))(RepositoryArchived(repo))
    }

  /** https://developer.github.com/v3/pulls/#create-a-pull-request */
  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] =
    client
      .postWithBody[PullRequestOut, NewPullRequestData](url.pulls(repo), data, modify(repo))
      .adaptErr(SecondaryRateLimitExceeded.fromThrowable)

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
  override def labelPullRequest(
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
}
