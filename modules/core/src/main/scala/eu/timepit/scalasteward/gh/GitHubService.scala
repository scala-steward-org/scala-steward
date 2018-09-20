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

package eu.timepit.scalasteward.gh

import better.files.File
import cats.effect.IO
import eu.timepit.scalasteward
import io.circe.parser

trait GitHubService[F[_]] {

  /** https://developer.github.com/v3/repos/forks/#create-a-fork */
  def fork(user: AuthenticatedUser, repo: GitHubRepo): F[GitHubRepoResponse]
}

object GitHubService {
  val curl: GitHubService[IO] =
    new GitHubService[IO] {
      override def fork(user: AuthenticatedUser, repo: GitHubRepo): IO[GitHubRepoResponse] = {
        val url = s"https://api.github.com/repos/${repo.owner}/${repo.repo}/forks"
        val cmd =
          List(
            "curl",
            "--silent",
            "--request",
            "POST",
            "--user",
            s"${user.login}:${user.accessToken}",
            url
          )
        scalasteward.io.exec(cmd, File.currentWorkingDirectory).flatMap { lines =>
          IO.fromEither(parser.decode[GitHubRepoResponse](lines.mkString))
        }
      }
    }
}
