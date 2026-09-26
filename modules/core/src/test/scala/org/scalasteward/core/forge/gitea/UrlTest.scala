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

import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

class UrlTest extends FunSuite {
  val url = new Url(uri"https://git.example.com/api/v1")

  val repo = Repo("scala-steward-org", "scala-steward")
  val branch = Branch("main")

  test("repos") {
    assertEquals(
      url.repos(repo).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward"
    )
  }

  test("branch") {
    assertEquals(
      url.repoBranch(repo, branch).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward/branches/main"
    )
  }

  test("forks") {
    assertEquals(
      url.forks(repo).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward/forks"
    )
  }

  test("pulls") {
    assertEquals(
      url.pulls(repo).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward/pulls"
    )
  }

  test("pull") {
    assertEquals(
      url.pull(repo, PullRequestNumber(1)).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward/pulls/1"
    )
  }
}
