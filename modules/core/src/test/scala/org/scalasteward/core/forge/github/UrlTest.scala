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

package org.scalasteward.core.forge.github

import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

class UrlTest extends FunSuite {
  private val url = new Url(uri"https://api.github.com")
  import url.*

  private val repo = Repo("fthomas", "refined")
  private val branch = Branch("master")

  test("branches") {
    assertEquals(
      branches(repo, branch).toString,
      "https://api.github.com/repos/fthomas/refined/branches/master"
    )
  }

  test("forks") {
    assertEquals(forks(repo).toString, "https://api.github.com/repos/fthomas/refined/forks")
  }

  test("listPullRequests") {
    assertEquals(
      listPullRequests(
        repo,
        "scala-steward:update/fs2-core-1.0.0",
        Branch("series/0.6.x")
      ).toString,
      "https://api.github.com/repos/fthomas/refined/pulls?head=scala-steward%3Aupdate/fs2-core-1.0.0&base=series/0.6.x&state=all"
    )
  }

  test("pulls") {
    assertEquals(pulls(repo).toString, "https://api.github.com/repos/fthomas/refined/pulls")
  }

  test("repos") {
    assertEquals(repos(repo).toString, "https://api.github.com/repos/fthomas/refined")
  }

  test("assignees") {
    assertEquals(
      assignees(repo, PullRequestNumber(1)).toString,
      "https://api.github.com/repos/fthomas/refined/issues/1/assignees"
    )
  }

  test("reviewers") {
    assertEquals(
      reviewers(repo, PullRequestNumber(1)).toString,
      "https://api.github.com/repos/fthomas/refined/pulls/1/requested_reviewers"
    )
  }
}
