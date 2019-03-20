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
import io.circe.Decoder
import org.http4s.Method.{GET, POST}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.Client
import org.http4s.{Request, Uri}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.github._
import org.scalasteward.core.github.data._

final class Http4sGitHubApiAlg[F[_]: Sync](
    client: Client[F],
    gitHubApiHost: Uri,
    modify: Request[F] => F[Request[F]]
) extends GitHubApiAlg[F] {
  val url = new Url(gitHubApiHost)

  override def createFork(repo: Repo): F[RepoOut] = {
    val req = Request[F](POST, url.forks(repo))
    expectJsonOf[RepoOut](req)
  }

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val req = Request[F](POST, url.pulls(repo)).withEntity(data)(jsonEncoderOf)
    expectJsonOf[PullRequestOut](req)
  }

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    get[BranchOut](url.branches(repo, branch))

  override def getRepo(repo: Repo): F[RepoOut] =
    get[RepoOut](url.repos(repo))

  override def listPullRequests(repo: Repo, head: String): F[List[PullRequestOut]] =
    get[List[PullRequestOut]](url.listPullRequests(repo, head))

  def get[A: Decoder](uri: Uri): F[A] =
    expectJsonOf[A](Request[F](GET, uri))

  def expectJsonOf[A: Decoder](req: Request[F]): F[A] =
    client.expect[A](modify(req))(jsonOf)
}
