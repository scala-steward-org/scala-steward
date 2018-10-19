/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.github.http4s

import cats.effect.Sync
import cats.implicits._
import eu.timepit.scalasteward.git.Branch
import eu.timepit.scalasteward.github._
import eu.timepit.scalasteward.github.data._
import eu.timepit.scalasteward.github.http4s.Http4sGitHubApiAlg._
import io.circe.Decoder
import org.http4s.Method.{GET, POST}
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Headers, Request}

class Http4sGitHubApiAlg[F[_]](
    implicit
    client: Client[F],
    user: AuthenticatedUser,
    F: Sync[F]
) extends GitHubApiAlg[F] {
  override def createFork(
      repo: Repo
  ): F[RepoOut] =
    http4sUrl.forks[F](repo).flatMap { uri =>
      val req = Request[F](POST, uri)
      expectJsonOf[RepoOut](req)
    }

  override def createPullRequest(
      repo: Repo,
      data: CreatePullRequestIn
  ): F[PullRequestOut] =
    http4sUrl.pulls[F](repo).flatMap { uri =>
      val req = Request[F](POST, uri).withEntity(data)
      expectJsonOf[PullRequestOut](req)
    }

  override def getBranch(
      repo: Repo,
      branch: Branch
  ): F[BranchOut] =
    http4sUrl.branches[F](repo, branch).flatMap { uri =>
      val req = Request[F](GET, uri)
      expectJsonOf[BranchOut](req)
    }

  override def listPullRequests(
      repo: Repo,
      head: String
  ): F[List[PullRequestOut]] =
    http4sUrl.listPullRequests[F](repo, head).flatMap { uri =>
      val req = Request[F](GET, uri)
      expectJsonOf[List[PullRequestOut]](req)
    }

  def expectJsonOf[A: Decoder](req: Request[F]): F[A] =
    client.expect[A](authenticate(user)(req))(jsonOf)
}

object Http4sGitHubApiAlg {
  def authenticate[F[_]](user: AuthenticatedUser)(req: Request[F]): Request[F] =
    req.withHeaders(req.headers ++ Headers(toBasicAuth(user)))

  def toBasicAuth(user: AuthenticatedUser): Authorization =
    Authorization(BasicCredentials(user.login, user.accessToken))
}
