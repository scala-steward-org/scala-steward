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

package eu.timepit.scalasteward.gh.http4s

import cats.effect.{Resource, Sync}
import cats.implicits._
import eu.timepit.scalasteward.gh._
import eu.timepit.scalasteward.gh.http4s.Http4sGitHubService._
import org.http4s.Method.POST
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Headers, Request}

class Http4sGitHubService[F[_]: Sync](client: Resource[F, Client[F]]) extends GitHubService[F] {
  override def fork(user: AuthenticatedUser, repo: GitHubRepo): F[GitHubRepoResponse] =
    ApiUrls.forks[F](repo).flatMap { uri =>
      val req = Request[F](POST, uri, headers = Headers(toBasicAuth(user)))
      client.use(_.expect[GitHubRepoResponse](req)(jsonOf))
    }
}

object Http4sGitHubService {
  def toBasicAuth(user: AuthenticatedUser): Authorization =
    Authorization(BasicCredentials(user.login, user.accessToken))
}
