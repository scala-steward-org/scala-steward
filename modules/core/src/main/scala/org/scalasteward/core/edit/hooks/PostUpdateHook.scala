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

package org.scalasteward.core.edit.hooks

import cats.implicits.*
import org.scalasteward.core.data.{ArtifactId, GroupId, Update}
import org.scalasteward.core.git.CommitMsg
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel

final case class PostUpdateHook(
    groupId: Option[GroupId],
    artifactId: Option[ArtifactId],
    command: Nel[String],
    useSandbox: Boolean,
    commitMessage: Update.Single => CommitMsg,
    enabledByCache: RepoCache => Boolean,
    enabledByConfig: RepoConfig => Boolean,
    addToGitBlameIgnoreRevs: Boolean
) {
  def showCommand: String = command.mkString_("'", " ", "'")
}
