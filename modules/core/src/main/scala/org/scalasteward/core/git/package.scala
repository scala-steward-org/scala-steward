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

package org.scalasteward.core

import org.scalasteward.core.data.{Repo, Update}
import org.scalasteward.core.repoconfig.CommitsConfig

package object git {
  type GitAlg[F[_]] = GenGitAlg[F, Repo]

  val gitBlameIgnoreRevsName = ".git-blame-ignore-revs"

  val defaultUpdateBranchPrefix = "update"
  val updateBranchPrefix = defaultUpdateBranchPrefix

  def branchFor(
      update: Update,
      baseBranch: Option[Branch] = None,
      updateBranchPrefix: String = defaultUpdateBranchPrefix
  ): Branch = {
    val prefix = updateBranchPrefix.stripSuffix("/") match {
      case ""     => ""
      case prefix => s"$prefix/"
    }
    val base = baseBranch.fold("")(branch => s"${branch.name}/")
    update.on(
      update = u => Branch(s"$prefix$base${u.name}-${u.nextVersion}"),
      grouped = g => {
        val hashString = Math.abs(g.updates.map(_.nextVersion).sortBy(_.value).hashCode()).toString
        val branch = s"$prefix$base${g.name}"
        Branch(branch.replace("${hash}", hashString))
      }
    )
  }

  def commitMsgFor(
      update: Update.Single,
      commitsConfig: CommitsConfig,
      branch: Option[Branch]
  ): CommitMsg =
    CommitMsg.replaceVariables(commitsConfig.messageOrDefault)(update, branch)
}
