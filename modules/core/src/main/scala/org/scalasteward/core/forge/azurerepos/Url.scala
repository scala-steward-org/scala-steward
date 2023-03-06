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

package org.scalasteward.core.forge.azurerepos

import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

class Url(apiHost: Uri, organization: String) {
  private val apiVersion = "7.1-preview.1"

  private val withoutPrefix: String => String = name => name.split("refs/heads/").last

  private def repos(repo: Repo): Uri =
    (apiHost / organization / repo.owner / "_apis/git/repositories" / repo.repo)
      .withQueryParam("api-version", apiVersion)

  def getRepo(repo: Repo): Uri =
    repos(repo).withQueryParam("includeParent", "true")

  def getBranch(repo: Repo, branch: Branch): Uri =
    (repos(repo) / "stats/branches").withQueryParam("name", withoutPrefix(branch.name))

  def pullRequests(repo: Repo): Uri =
    repos(repo) / "pullrequests"

  def listPullRequests(repo: Repo, source: String, target: Branch): Uri =
    pullRequests(repo).withQueryParams(
      Map("searchCriteria.sourceRefName" -> source, "searchCriteria.targetRefName" -> target.name)
    )

  def closePullRequest(repo: Repo, number: PullRequestNumber): Uri =
    pullRequests(repo) / number.value

  def commentPullRequest(repo: Repo, number: PullRequestNumber): Uri =
    pullRequests(repo) / number.value / "threads"
}
