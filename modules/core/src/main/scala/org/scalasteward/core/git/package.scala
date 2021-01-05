/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core

import org.scalasteward.core.data.Update
import org.scalasteward.core.repoconfig.CommitsConfig
import org.scalasteward.core.update.show
import org.scalasteward.core.vcs.data.Repo

package object git {
  type GitAlg[F[_]] = GenGitAlg[F, Repo]

  def branchFor(update: Update): Branch =
    Branch(s"update/${update.name}-${update.nextVersion}")

  def commitMsgFor(update: Update, commitsConfig: CommitsConfig): String = {
    val artifact = show.oneLiner(update)
    val defaultMessage = s"Update $artifact to ${update.nextVersion}"
    commitsConfig.messageOrDefault
      .replace("${default}", defaultMessage)
      .replace("${artifactName}", artifact)
      .replace("${currentVersion}", update.currentVersion)
      .replace("${nextVersion}", update.nextVersion)
  }
}
