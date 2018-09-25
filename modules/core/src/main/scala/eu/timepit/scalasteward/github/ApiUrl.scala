/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.github

import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.model.Branch

object ApiUrl {
  def branches(repo: Repo, branch: Branch): String =
    reposPart(repo) + s"/branches/${branch.name}"

  def forks(repo: Repo): String =
    reposPart(repo) + "/forks"

  def pulls(repo: Repo): String =
    reposPart(repo) + "/pulls"

  val hostPart: String =
    "https://api.github.com"

  def reposPart(repo: Repo): String =
    s"$hostPart/repos/${repo.owner}/${repo.repo}"
}
