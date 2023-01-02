package org.scalasteward.core.vcs

import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.scalasteward.core.application.Config.VCSCfg
import org.scalasteward.core.data.ReleaseRelatedUrl._
import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.{MockEff, MockState}

class VCSExtraAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  private val state = MockState.empty.copy(clientResponses = HttpApp {
    case HEAD -> Root / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0" => Ok("exist")
    case HEAD -> Root / "foo" / "buz" / "compare" / "v0.1.0...v0.2.0" => PermanentRedirect()
    case _                                                            => NotFound()
  })

  private val v1 = Version("0.1.0")
  private val v2 = Version("0.2.0")

  test("getReleaseRelatedUrls: repoUrl not found") {
    val obtained =
      vcsExtraAlg.getReleaseRelatedUrls(uri"https://github.com/foo/foo", v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  test("getReleaseRelatedUrls: repoUrl ok") {
    val obtained =
      vcsExtraAlg.getReleaseRelatedUrls(uri"https://github.com/foo/bar", v1, v2).runA(state)
    val expected = List(VersionDiff(uri"https://github.com/foo/bar/compare/v0.1.0...v0.2.0"))
    assertIO(obtained, expected)
  }

  test("getReleaseRelatedUrls: repoUrl permanent redirect") {
    val obtained =
      vcsExtraAlg.getReleaseRelatedUrls(uri"https://github.com/foo/buz", v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  private val config = VCSCfg(
    VCSType.GitHub,
    uri"https://github.on-prem.com/",
    "",
    doNotFork = false,
    addLabels = false
  )
  private val githubOnPremVcsExtraAlg = VCSExtraAlg.create[MockEff](config)

  test("getReleaseRelatedUrls: on-prem, repoUrl not found") {
    val obtained = githubOnPremVcsExtraAlg
      .getReleaseRelatedUrls(uri"https://github.on-prem.com/foo/foo", v1, v2)
      .runA(state)
    assertIO(obtained, List.empty)
  }

  test("getReleaseRelatedUrls: on-prem, repoUrl ok") {
    val obtained = githubOnPremVcsExtraAlg
      .getReleaseRelatedUrls(uri"https://github.on-prem.com/foo/bar", v1, v2)
      .runA(state)
    val expected =
      List(VersionDiff(uri"https://github.on-prem.com/foo/bar/compare/v0.1.0...v0.2.0"))
    assertIO(obtained, expected)
  }

  test("getReleaseRelatedUrls: on-prem, repoUrl permanent redirect") {
    val obtained = githubOnPremVcsExtraAlg
      .getReleaseRelatedUrls(uri"https://github.on-prem.com/foo/buz", v1, v2)
      .runA(state)
    assertIO(obtained, List.empty)
  }
}
