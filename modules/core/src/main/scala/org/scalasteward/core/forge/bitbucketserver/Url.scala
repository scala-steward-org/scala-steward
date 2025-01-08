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

package org.scalasteward.core.forge.bitbucketserver

import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

final class Url(apiHost: Uri) {
  private val api: Uri = apiHost / "rest" / "api" / "1.0"
  private val defaultReviewers: Uri = apiHost / "rest" / "default-reviewers" / "1.0"

  def branches(repo: Repo): Uri =
    repos(repo) / "branches"

  def comments(repo: Repo, number: PullRequestNumber): Uri =
    pullRequest(repo, number) / "comments"

  def declinePullRequest(repo: Repo, number: PullRequestNumber, version: Int): Uri =
    (pullRequest(repo, number) / "decline").withQueryParam("version", version)

  def defaultBranch(repo: Repo): Uri =
    branches(repo) / "default"

  def listBranch(repo: Repo, branch: Branch): Uri =
    branches(repo).withQueryParam("filterText", branch.name)

  def listPullRequests(repo: Repo, head: String): Uri =
    pullRequests(repo)
      .withQueryParam("at", head)
      .withQueryParam("limit", "1000")
      .withQueryParam("direction", "outgoing")
      .withQueryParam("state", "all")

  def pullRequest(repo: Repo, number: PullRequestNumber): Uri =
    pullRequests(repo) / number.toString

  def pullRequests(repo: Repo): Uri =
    repos(repo) / "pull-requests"

  def repos(repo: Repo): Uri =
    api / "projects" / repo.owner / "repos" / repo.repo

  def reviewers(repo: Repo): Uri =
    defaultReviewers / "projects" / repo.owner / "repos" / repo.repo / "conditions"
}
