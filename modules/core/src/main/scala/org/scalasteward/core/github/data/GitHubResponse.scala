/*
 * Copyright 2018-2020 Scala Steward contributors
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

package org.scalasteward.core.github.data

import io.circe.Decoder
import io.circe.generic.semiauto._
import org.scalasteward.core.util.uri.uriDecoder
import org.scalasteward.core.vcs.data._
import org.scalasteward.core.application.SupportedVCS.GitHub
import org.http4s.Uri
import org.scalasteward.core.git.Branch

final case class GitHubResponse(
    name: String,
    owner: UserOut,
    parent: Option[GitHubResponse],
    clone_url: Uri,
    default_branch: Branch
) {
  def toRepoOut: RepoOut =
    RepoOut(GitHub, name, owner, parent.map(_.toRepoOut), clone_url, default_branch)
}

object GitHubResponse {

  implicit val githubResponseDecoder: Decoder[GitHubResponse] = deriveDecoder
}
