/*
 * Copyright 2018-2019 scala-steward contributors
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

import cats.effect.Sync
import org.http4s.client.Client
import org.http4s.{Request, Uri}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.github._
import org.scalasteward.core.github.data._
import org.scalasteward.core.util.HttpJsonClient

final class Http4sGitHubApiAlg[F[_]: Sync](
    client: Client[F],
    gitHubApiHost: Uri,
    modify: Request[F] => F[Request[F]]
) extends GitHubApiAlg[F] {
  private val jsonClient = new HttpJsonClient[F](client, modify)
  private val url = new Url(gitHubApiHost)

  override def createFork(repo: Repo): F[RepoOut] =
    jsonClient.post(url.forks(repo))

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] =
    jsonClient.postWithBody(url.pulls(repo), data)

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    jsonClient.get(url.branches(repo, branch))

  override def getRepo(repo: Repo): F[RepoOut] =
    jsonClient.get(url.repos(repo))

  override def listPullRequests(repo: Repo, head: String): F[List[PullRequestOut]] =
    jsonClient.get(url.listPullRequests(repo, head))
}
