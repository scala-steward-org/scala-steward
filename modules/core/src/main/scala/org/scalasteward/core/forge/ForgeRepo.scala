/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.core.forge

import org.http4s.Uri
import org.scalasteward.core.application.Config.ForgeCfg

/** ForgeRepo encapsulates two concepts that are commonly considered together - the URI of a repo,
  * and the 'type' of forge that url represents. Given a URI, once we know it's a GitHub or GitLab
  * forge, etc, then we can know how to construct many of the urls for common resources existing at
  * that repo host- for instance, the url to view a particular file, or to diff two commits.
  */
case class ForgeRepo(forgeType: ForgeType, repoUrl: Uri) {
  def diffUrlFor(from: String, to: String): Uri = forgeType.diffs.forDiff(from, to)(repoUrl)

  def fileUrlFor(fileName: String): Uri = forgeType.files.forFile(fileName)(repoUrl)
}

object ForgeRepo {
  def fromRepoUrl(repoUrl: Uri)(implicit config: ForgeCfg): Option[ForgeRepo] = for {
    repoForgeType <- ForgeType.fromRepoUrl(repoUrl)
  } yield ForgeRepo(repoForgeType, repoUrl)
}
