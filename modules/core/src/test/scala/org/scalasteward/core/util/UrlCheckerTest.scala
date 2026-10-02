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

package org.scalasteward.core.util

import cats.syntax.all.*
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.http4s.syntax.literals.*
import org.scalasteward.core.coursier.DependencyMetadata
import org.scalasteward.core.data.Version
import org.scalasteward.core.forge.github.Repository
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.{GitHubAuth, MockEff, MockEffOps, MockState}
import org.scalasteward.core.nurture.UpdateInfoUrl.*

// The mock config has a GitHub forge with api host http://example.com.
class UrlCheckerTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  private val httpApp = HttpApp[MockEff] {
    case HEAD -> Root / "repos" / "foo" / "bar"                                  => Ok()
    case HEAD -> Root / "repos" / "foo" / "bar" / "releases" / "tags" / "v0.2.0" => Ok()
    case HEAD -> Root / "repos" / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0"  => Ok()
    case HEAD -> Root / "repos" / "foo" / "bar" / "contents" / "CHANGELOG.md"    => Ok()
    case HEAD -> "repos" /: _                                                    => NotFound()
    case HEAD -> Root / "public" / "repo"                                        => Ok()
    // web UI of a forge that requires a session cookie
    case _ => Found()
  }
  private val authApp = GitHubAuth.api(List(Repository("foo/bar")))
  private val state = MockState.empty.copy(clientResponses = authApp <+> httpApp)

  test("exists: urls on the configured forge are checked via its API") {
    val urls = List(
      uri"http://example.com/foo/bar",
      uri"http://example.com/foo/bar/releases/tag/v0.2.0",
      uri"http://example.com/foo/bar/compare/v0.1.0...v0.2.0",
      uri"http://example.com/foo/bar/blob/master/CHANGELOG.md"
    )
    val obtained = urls.traverse(urlChecker.exists).runA(state)
    assertIO(obtained, List(true, true, true, true))
  }

  test("exists: missing resources on the configured forge") {
    val urls = List(
      uri"http://example.com/foo/baz",
      uri"http://example.com/foo/bar/releases/tag/v0.3.0",
      uri"http://example.com/foo/bar/blob/master/RELEASES.md"
    )
    val obtained = urls.traverse(urlChecker.exists).runA(state)
    assertIO(obtained, List(false, false, false))
  }

  test("exists: untranslatable urls on the configured forge are checked directly") {
    val obtained = urlChecker.exists(uri"http://example.com/foo/bar/wiki/Home").runA(state)
    assertIO(obtained, false)
  }

  test("exists: urls on other hosts are checked directly") {
    val obtained = List(uri"https://other.example.org/public/repo", uri"https://github.com/foo/bar")
      .traverse(urlChecker.exists)
      .runA(state)
    assertIO(obtained, List(true, false))
  }

  test("findUpdateInfoUrls: dependency hosted on the configured forge") {
    val metadata = DependencyMetadata.empty.copy(scmUrl = uri"http://example.com/foo/bar".some)
    val update = Version.Update(Version("0.1.0"), Version("0.2.0"))
    val obtained = (for {
      filtered <- metadata.filterUrls(urlChecker.exists)
      urls <- updateInfoUrlFinder.findUpdateInfoUrls(filtered, update)
    } yield urls).runA(state)
    val expected = List(
      GitHubReleaseNotes(uri"http://example.com/foo/bar/releases/tag/v0.2.0"),
      CustomChangelog(uri"http://example.com/foo/bar/blob/master/CHANGELOG.md"),
      VersionDiff(uri"http://example.com/foo/bar/compare/v0.1.0...v0.2.0")
    )
    assertIO(obtained, expected)
  }
}
