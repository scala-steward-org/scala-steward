/*
 * Copyright 2018-2025 Scala Steward contributors
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

package org.scalasteward.core.mock

import io.circe.syntax.EncoderOps
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.scalasteward.core.forge.github.{InstallationOut, RepositoriesOut, Repository, TokenOut}

object GitHubAuth extends Http4sDsl[MockEff] {
  def api(repositories: List[Repository]): HttpApp[MockEff] = HttpApp[MockEff] { req =>
    (req: @unchecked) match {
      case GET -> Root / "app" / "installations" =>
        Ok(List(InstallationOut(1L)).asJson.spaces2)
      case POST -> Root / "app" / "installations" / "1" / "access_tokens" =>
        Ok(TokenOut("some-token").asJson.spaces2)
      case GET -> Root / "installation" / "repositories" =>
        Ok(RepositoriesOut(repositories).asJson.spaces2)
    }
  }
}
