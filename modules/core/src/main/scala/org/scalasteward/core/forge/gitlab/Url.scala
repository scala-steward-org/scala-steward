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

package org.scalasteward.core.forge.gitlab

import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

class Url(apiHost: Uri) {
  def encodedProjectId(repo: Repo): String = s"${repo.owner}%2F${repo.repo}"

  def getBranch(repo: Repo, branch: Branch): Uri =
    repos(repo) / "repository" / "branches" / branch.name

  def createFork(repo: Repo): Uri =
    repos(repo) / "fork"

  def forks(repo: Repo): Uri =
    repos(repo) / "forks"

  def mergeRequest(repo: Repo): Uri =
    repos(repo) / "merge_requests"

  def existingMergeRequest(repo: Repo, number: PullRequestNumber): Uri =
    mergeRequest(repo) / number.toString

  def mergeWhenPiplineSucceeds(repo: Repo, number: PullRequestNumber): Uri =
    (existingMergeRequest(repo, number) / "merge")
      .withQueryParam("merge_when_pipeline_succeeds", "true")

  def requiredApprovals(repo: Repo, number: PullRequestNumber, approvalsRequired: Int): Uri =
    (existingMergeRequest(repo, number) / "approvals")
      .withQueryParam("approvals_required", approvalsRequired)

  def listMergeRequests(repo: Repo, source: String, target: String): Uri =
    mergeRequest(repo)
      .withQueryParam("source_branch", source)
      .withQueryParam("target_branch", target)

  def repos(repo: Repo): Uri =
    apiHost / "projects" / s"${repo.owner}/${repo.repo}"

  def comments(repo: Repo, number: PullRequestNumber): Uri =
    existingMergeRequest(repo, number) / "notes"

  def users: Uri =
    apiHost / "users"
}
