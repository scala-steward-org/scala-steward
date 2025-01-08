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

package org.scalasteward.core.forge.bitbucket

import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

private[bitbucket] class Url(apiHost: Uri) {
  def forks(rep: Repo): Uri =
    repo(rep) / "forks"

  def listPullRequests(rep: Repo, head: String): Uri =
    pullRequests(rep).withQueryParam("q", s"""source.branch.name = "$head" """)

  def pullRequests(rep: Repo): Uri =
    repo(rep) / "pullrequests"

  def pullRequest(rep: Repo, number: PullRequestNumber): Uri =
    repo(rep) / "pullrequests" / number.toString

  def branch(rep: Repo, branch: Branch): Uri =
    repo(rep) / "refs" / "branches" / branch.name

  def repo(repo: Repo): Uri =
    apiHost / "repositories" / repo.owner / repo.repo

  def comments(rep: Repo, number: PullRequestNumber): Uri =
    pullRequest(rep, number) / "comments"

  def decline(rep: Repo, number: PullRequestNumber): Uri =
    pullRequest(rep, number) / "decline"

  def defaultReviewers(rep: Repo): Uri =
    repo(rep) / "default-reviewers"
}
