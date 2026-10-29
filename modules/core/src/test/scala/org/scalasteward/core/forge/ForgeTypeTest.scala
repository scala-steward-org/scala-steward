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

package org.scalasteward.core.forge

import munit.FunSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.{Repo, Update}
import org.scalasteward.core.forge.ForgeType.{GitHub, GitLab}
import org.scalasteward.core.git
import org.scalasteward.core.util.Nel

class ForgeTypeTest extends FunSuite {
  private val repo = Repo("foo", "bar")

  // Single updates

  {
    val update = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val updateBranch = git.branchFor(update, None)

    test("headFor (single)") {
      assertEquals(GitHub.pullRequestHeadFor(repo, updateBranch), s"foo:${updateBranch.name}")
      assertEquals(GitLab.pullRequestHeadFor(repo, updateBranch), updateBranch.name)
    }
  }

  // Grouped updates

  {
    val update = Update.Grouped(
      name = "my-group",
      title = None,
      updates = Nel.of(("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single)
    )

    val updateBranch = git.branchFor(update, None)

    test("headFor (grouped)") {
      assertEquals(GitHub.pullRequestHeadFor(repo, updateBranch), s"foo:update/my-group")
      assertEquals(GitLab.pullRequestHeadFor(repo, updateBranch), updateBranch.name)
    }
  }

  // Grouped updates with hash

  {
    val update = Update.Grouped(
      name = "my-group-${hash}",
      title = None,
      updates = Nel.of(("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single)
    )

    val updateBranch = git.branchFor(update, None)

    test("headFor (grouped) with $hash") {
      assertEquals(GitHub.pullRequestHeadFor(repo, updateBranch), s"foo:update/my-group-1164623676")
      assertEquals(GitLab.pullRequestHeadFor(repo, updateBranch), updateBranch.name)
    }
  }

  // existenceApiUrl

  {
    val apiHost = uri"https://ghe.example.com/api/v3"
    val webRepo = uri"https://ghe.example.com/foo/bar"

    test("existenceApiUrl (GitHub): repo") {
      assertEquals(
        GitHub.existenceApiUrl(apiHost, webRepo),
        Some(uri"https://ghe.example.com/api/v3/repos/foo/bar")
      )
    }

    test("existenceApiUrl (GitHub): file") {
      assertEquals(
        GitHub.existenceApiUrl(apiHost, webRepo / "blob" / "main" / "docs" / "CHANGELOG.md"),
        Some(uri"https://ghe.example.com/api/v3/repos/foo/bar/contents/docs/CHANGELOG.md?ref=main")
      )
    }

    test("existenceApiUrl (GitHub): file with fragment") {
      assertEquals(
        GitHub.existenceApiUrl(
          apiHost,
          (webRepo / "blob" / "master" / "README.md").withFragment("changelog")
        ),
        Some(uri"https://ghe.example.com/api/v3/repos/foo/bar/contents/README.md?ref=master")
      )
    }

    test("existenceApiUrl (GitHub): release") {
      assertEquals(
        GitHub.existenceApiUrl(apiHost, webRepo / "releases" / "tag" / "v1.2.3"),
        Some(uri"https://ghe.example.com/api/v3/repos/foo/bar/releases/tags/v1.2.3")
      )
    }

    test("existenceApiUrl (GitHub): compare") {
      assertEquals(
        GitHub.existenceApiUrl(apiHost, webRepo / "compare" / "v1.2.2...v1.2.3"),
        Some(uri"https://ghe.example.com/api/v3/repos/foo/bar/compare/v1.2.2...v1.2.3")
      )
    }

    test("existenceApiUrl (GitHub): unknown paths") {
      assertEquals(GitHub.existenceApiUrl(apiHost, uri"https://ghe.example.com/foo"), None)
      assertEquals(GitHub.existenceApiUrl(apiHost, webRepo / "blob" / "main"), None)
      assertEquals(GitHub.existenceApiUrl(apiHost, webRepo / "wiki" / "Home"), None)
    }

    test("existenceApiUrl (other forges)") {
      assertEquals(GitLab.existenceApiUrl(apiHost, webRepo), None)
    }
  }
}
