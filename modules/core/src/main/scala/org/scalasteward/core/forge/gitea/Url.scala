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

package org.scalasteward.core.forge.gitea

import org.http4s.Uri
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

class Url(apiHost: Uri) {
  def repoBranch(repo: Repo, branch: Branch): Uri =
    repos(repo) / "branches" / branch.name

  def forks(repo: Repo): Uri =
    repos(repo) / "forks"

  def pulls(repo: Repo): Uri =
    repos(repo) / "pulls"

  def pull(repo: Repo, number: PullRequestNumber): Uri =
    repos(repo) / "pulls" / number.toString

  def comments(repo: Repo, number: PullRequestNumber): Uri =
    repos(repo) / "issues" / number.toString / "comments"

  def labels(repo: Repo): Uri =
    repos(repo) / "labels"

  def pullRequestLabels(repo: Repo, number: PullRequestNumber): Uri =
    repos(repo) / "issues" / number.toString / "labels"

  def repos(repo: Repo): Uri =
    apiHost / "repos" / repo.owner / repo.repo

}
