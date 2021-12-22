/*
 * Copyright 2018-2021 Scala Steward contributors
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

import org.http4s.{Request, Uri}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.data._

final class GitHubApiAlg[F[_]](
    gitHubApiHost: Uri,
    modify: Repo => Request[F] => F[Request[F]]
)(implicit
    client: HttpJsonClient[F]
) extends VCSApiAlg[F] {
  private val url = new Url(gitHubApiHost)

  /** https://developer.github.com/v3/repos/forks/#create-a-fork */
  override def createFork(repo: Repo): F[RepoOut] =
    client.post(url.forks(repo), modify(repo))

  /** https://developer.github.com/v3/pulls/#create-a-pull-request */
  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] =
    client.postWithBody(url.pulls(repo), data, modify(repo))

  /** https://developer.github.com/v3/repos/branches/#get-branch */
  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.branches(repo, branch), modify(repo))

  /** https://developer.github.com/v3/repos/#get */
  override def getRepo(repo: Repo): F[RepoOut] =
    client.get(url.repos(repo), modify(repo))

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

}
