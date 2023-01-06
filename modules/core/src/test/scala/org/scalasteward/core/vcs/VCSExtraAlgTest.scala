package org.scalasteward.core.vcs

import cats.syntax.all._
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.scalasteward.core.application.Config.VCSCfg
import org.scalasteward.core.coursier.DependencyMetadata
import org.scalasteward.core.data.ReleaseRelatedUrl.{CustomReleaseNotes, VersionDiff}
import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.{MockEff, MockState}

class VCSExtraAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  private val state = MockState.empty.copy(clientResponses = HttpApp {
    case HEAD -> Root / "foo" / "bar" / "README.md"                        => Ok()
    case HEAD -> Root / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0"      => Ok()
    case HEAD -> Root / "foo" / "bar1" / "blob" / "master" / "RELEASES.md" => Ok()
    case HEAD -> Root / "foo" / "buz" / "compare" / "v0.1.0...v0.2.0"      => PermanentRedirect()
    case _                                                                 => NotFound()
  })

  private val v1 = Version("0.1.0")
  private val v2 = Version("0.2.0")

  test("getReleaseRelatedUrls: repoUrl not found") {
    val metadata = DependencyMetadata.empty.copy(scmUrl = uri"https://github.com/foo/foo".some)
    val obtained = vcsExtraAlg.getReleaseRelatedUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  test("getReleaseRelatedUrls: repoUrl ok") {
    val metadata = DependencyMetadata.empty.copy(scmUrl = uri"https://github.com/foo/bar".some)
    val obtained = vcsExtraAlg.getReleaseRelatedUrls(metadata, v1, v2).runA(state)
    val expected = List(VersionDiff(uri"https://github.com/foo/bar/compare/v0.1.0...v0.2.0"))
    assertIO(obtained, expected)
  }

  test("getReleaseRelatedUrls: repoUrl and releaseNotesUrl ok") {
    val metadata = DependencyMetadata.empty.copy(
      scmUrl = uri"https://github.com/foo/bar".some,
      releaseNotesUrl = uri"https://github.com/foo/bar/README.md#changelog".some
    )
    val obtained = vcsExtraAlg.getReleaseRelatedUrls(metadata, v1, v2).runA(state)
    val expected = List(
      CustomReleaseNotes(metadata.releaseNotesUrl.get),
      VersionDiff(uri"https://github.com/foo/bar/compare/v0.1.0...v0.2.0")
    )
    assertIO(obtained, expected)
  }

  test("getReleaseRelatedUrls: releaseNotesUrl is in possibleReleaseRelatedUrls") {
    val metadata = DependencyMetadata.empty.copy(
      scmUrl = uri"https://github.com/foo/bar1".some,
      releaseNotesUrl = uri"https://github.com/foo/bar1/blob/master/RELEASES.md".some
    )
    val obtained = vcsExtraAlg.getReleaseRelatedUrls(metadata, v1, v2).runA(state)
    val expected = List(CustomReleaseNotes(metadata.releaseNotesUrl.get))
    assertIO(obtained, expected)
  }

  test("getReleaseRelatedUrls: repoUrl permanent redirect") {
    val metadata = DependencyMetadata.empty.copy(scmUrl = uri"https://github.com/foo/buz".some)
    val obtained = vcsExtraAlg.getReleaseRelatedUrls(metadata, v1, v2).runA(state)
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
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/foo".some)
    val obtained = githubOnPremVcsExtraAlg.getReleaseRelatedUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  test("getReleaseRelatedUrls: on-prem, repoUrl ok") {
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/bar".some)
    val obtained = githubOnPremVcsExtraAlg.getReleaseRelatedUrls(metadata, v1, v2).runA(state)
    val expected =
      List(VersionDiff(uri"https://github.on-prem.com/foo/bar/compare/v0.1.0...v0.2.0"))
    assertIO(obtained, expected)
  }

  test("getReleaseRelatedUrls: on-prem, repoUrl permanent redirect") {
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/buz".some)
    val obtained = githubOnPremVcsExtraAlg.getReleaseRelatedUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }
}
