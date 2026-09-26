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

package org.scalasteward.core.forge.azurerepos

import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

class UrlTest extends FunSuite {
  private val url = new Url(uri"https://dev.azure.com", "my-azure-org")

  private val repo = Repo("scala-steward-org", "scala-steward")
  private val branch = Branch("/refs/heads/main")

  test("getRepo") {
    assertEquals(
      url.getRepo(repo).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward?api-version=7.1-preview.1&includeParent=true"
    )
  }

  test("pullRequests") {
    assertEquals(
      url.pullRequests(repo).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/pullrequests?api-version=7.1-preview.1"
    )
  }

  test("getBranch") {
    assertEquals(
      url.getBranch(repo, branch).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/stats%2Fbranches?api-version=7.1-preview.1&name=main"
    )
  }

  test("listPullRequests") {
    assertEquals(
      url.listPullRequests(repo, "update/sbt-1.7.1", Branch("main")).toString(),
      "https://dev.azure.com/my-azure-org/scala-steward-org/_apis%2Fgit%2Frepositories/scala-steward/pullrequests" +
        "?api-version=7.1-preview.1&searchCriteria.sourceRefName=update/sbt-1.7.1&searchCriteria.targetRefName=main"
    )
  }

  test("closePullRequest") {
    assertEquals(
      url.closePullRequest(repo, PullRequestNumber(1221)).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/pullrequests/1221?api-version=7.1-preview.1"
    )
  }

  test("commentPullRequest") {
    assertEquals(
      url.commentPullRequest(repo, PullRequestNumber(42)).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/pullrequests/42/threads?api-version=7.1-preview.1"
    )
  }

}
