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

package eu.timepit.scalasteward.application

import better.files.File
import cats.effect.Sync
import cats.implicits._
import eu.timepit.scalasteward.git.Author
import eu.timepit.scalasteward.github.data.AuthenticatedUser

final case class Config(
    workspace: File,
    gitAuthor: Author,
    gitHubLogin: String,
    gitHubTokenFile: File
) {
  def gitHubUser[F[_]](implicit F: Sync[F]): F[AuthenticatedUser] =
    F.delay(AuthenticatedUser(gitHubLogin, gitHubTokenFile.contentAsString.trim))
}

object Config {
  def default[F[_]](implicit F: Sync[F]): F[Config] =
    F.delay(File.home).map { home =>
      val login = "scala-steward"
      Config(
        workspace = home / "code/scala-steward/workspace",
        gitAuthor = Author("Scala steward", "scala-steward@timepit.eu"),
        gitHubLogin = login,
        gitHubTokenFile = home / s".github/tokens/$login"
      )
    }
}
