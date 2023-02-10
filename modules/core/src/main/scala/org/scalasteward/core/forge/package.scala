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

package org.scalasteward.core

import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeType._
import org.scalasteward.core.git.Branch

package object forge {

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for searching
    * for already existing pull requests.
    */
  def listingBranch(forgeType: ForgeType, fork: Repo, updateBranch: Branch): String =
    forgeType match {
      case GitHub =>
        s"${fork.owner}/${fork.repo}:${updateBranch.name}"

      case GitLab | Bitbucket | BitbucketServer | AzureRepos | Gitea =>
        updateBranch.name
    }

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for creating a
    * new pull requests.
    */
  def createBranch(forgeType: ForgeType, fork: Repo, updateBranch: Branch): String =
    forgeType match {
      case GitHub =>
        s"${fork.owner}:${updateBranch.name}"

      case GitLab | Bitbucket | BitbucketServer | AzureRepos | Gitea =>
        updateBranch.name
    }
}
