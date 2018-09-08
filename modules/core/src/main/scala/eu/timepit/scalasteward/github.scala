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

package eu.timepit.scalasteward

import better.files.File
import cats.effect.IO

object github {
  val myLogin: String =
    "scala-steward"

  val accessToken: IO[String] =
    IO((File.home / s".github/tokens/$myLogin").contentAsString.trim)

  def fork(repo: GithubRepo): IO[List[String]] =
    accessToken.flatMap { token =>
      io.exec(
        List(
          "curl",
          "-X",
          "POST",
          "-u",
          s"$myLogin:$token",
          s"https://api.github.com/repos/${repo.owner}/${repo.repo}/forks"
        ),
        File.currentWorkingDirectory
      )
    }
}
