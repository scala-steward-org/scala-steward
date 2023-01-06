/*
 * Copyright 2018-2022 Scala Steward contributors
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

import org.scalasteward.core.git.Branch
import org.scalasteward.core.vcs.VCSType._
import org.scalasteward.core.vcs.data.Repo

package object vcs {

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for searching
    * for already existing pull requests.
    */
  def listingBranch(vcsType: VCSType, fork: Repo, updateBranch: Branch): String =
    vcsType match {
      case GitHub =>
        s"${fork.owner}/${fork.repo}:${updateBranch.name}"

      case GitLab | Bitbucket | BitbucketServer | AzureRepos =>
        updateBranch.name
    }

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for creating a
    * new pull requests.
    */
  def createBranch(vcsType: VCSType, fork: Repo, updateBranch: Branch): String =
    vcsType match {
      case GitHub =>
        s"${fork.owner}:${updateBranch.name}"

      case GitLab | Bitbucket | BitbucketServer | AzureRepos =>
        updateBranch.name
    }
}
