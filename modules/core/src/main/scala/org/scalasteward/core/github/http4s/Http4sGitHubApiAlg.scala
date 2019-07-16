/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.github.http4s

import org.http4s.{Request, Uri}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.github._
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.data._

final class Http4sGitHubApiAlg[F[_]](
    gitHubApiHost: Uri,
    modify: Repo => Request[F] => F[Request[F]]
)(
    implicit
    client: HttpJsonClient[F]
) extends VCSApiAlg[F] {
  private val url = new Url(gitHubApiHost)

  override def createFork(repo: Repo): F[RepoOut] =
    client.post(url.forks(repo), modify(repo))

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] =
    client.postWithBody(url.pulls(repo), data, modify(repo))

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.branches(repo, branch), modify(repo))

  override def getRepo(repo: Repo): F[RepoOut] =
    client.get(url.repos(repo), modify(repo))

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client.get(url.listPullRequests(repo, head, base), modify(repo))
}
