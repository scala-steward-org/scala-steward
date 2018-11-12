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
import scala.sys.process.Process

/** Configuration for scala-steward.
  *
  * == [[gitHubApiHost]] ==
  * REST API v3 endpoints prefix
  *
  * For github.com this is "https://api.github.com", see
  * [[https://developer.github.com/v3/]].
  *
  * For GitHub Enterprise this is "http(s)://[hostname]/api/v3", see
  * [[https://developer.github.com/enterprise/v3/]].
  *
  * == [[gitAskPass]] ==
  * Program that is invoked by scala-steward and git (via the `GIT_ASKPASS`
  * environment variable) to request the password for the user [[gitHubLogin]].
  *
  * This program could just be a simple shell script that echos the password.
  *
  * See also [[https://git-scm.com/docs/gitcredentials]].
  */
final case class Config(
    workspace: File,
    gitAuthor: Author,
    gitHubApiHost: String,
    gitHubLogin: String,
    gitAskPass: File
) {
  def gitHubUser[F[_]](implicit F: Sync[F]): F[AuthenticatedUser] =
    F.delay {
      val password = Process(gitAskPass.pathAsString).!!.trim
      AuthenticatedUser(gitHubLogin, password)
    }
}

object Config {
  def default[F[_]](implicit F: Sync[F]): F[Config] =
    F.delay(File.home).map { home =>
      val login = "scala-steward"
      Config(
        workspace = home / s"code/$login/workspace",
        gitAuthor = Author("Scala steward", s"$login@timepit.eu"),
        gitHubApiHost = "https://api.github.com",
        gitHubLogin = login,
        gitAskPass = home / s".github/askpass/$login.sh"
      )
    }
}
