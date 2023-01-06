package org.scalasteward.core.nurture

import cats.syntax.all._
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.application.Config.VCSCfg
import org.scalasteward.core.coursier.DependencyMetadata
import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.nurture.UpdateInfoUrl._
import org.scalasteward.core.nurture.UpdateInfoUrlFinder._
import org.scalasteward.core.vcs.VCSType
import org.scalasteward.core.vcs.VCSType._

class UpdateInfoUrlFinderTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  private val state = MockState.empty.copy(clientResponses = HttpApp {
    case HEAD -> Root / "foo" / "bar" / "README.md"                        => Ok()
    case HEAD -> Root / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0"      => Ok()
    case HEAD -> Root / "foo" / "bar1" / "blob" / "master" / "RELEASES.md" => Ok()
    case HEAD -> Root / "foo" / "buz" / "compare" / "v0.1.0...v0.2.0"      => PermanentRedirect()
    case _                                                                 => NotFound()
  })

  private val v1 = Version("0.1.0")
  private val v2 = Version("0.2.0")

  test("findUpdateInfoUrls: repoUrl not found") {
    val metadata = DependencyMetadata.empty.copy(scmUrl = uri"https://github.com/foo/foo".some)
    val obtained = updateInfoUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  test("findUpdateInfoUrls: repoUrl ok") {
    val metadata = DependencyMetadata.empty.copy(scmUrl = uri"https://github.com/foo/bar".some)
    val obtained = updateInfoUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    val expected = List(VersionDiff(uri"https://github.com/foo/bar/compare/v0.1.0...v0.2.0"))
    assertIO(obtained, expected)
  }

  test("findUpdateInfoUrls: repoUrl and releaseNotesUrl ok") {
    val metadata = DependencyMetadata.empty.copy(
      scmUrl = uri"https://github.com/foo/bar".some,
      releaseNotesUrl = uri"https://github.com/foo/bar/README.md#changelog".some
    )
    val obtained = updateInfoUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    val expected = List(
      CustomReleaseNotes(metadata.releaseNotesUrl.get),
      VersionDiff(uri"https://github.com/foo/bar/compare/v0.1.0...v0.2.0")
    )
    assertIO(obtained, expected)
  }

  test("findUpdateInfoUrls: releaseNotesUrl is in possibleReleaseRelatedUrls") {
    val metadata = DependencyMetadata.empty.copy(
      scmUrl = uri"https://github.com/foo/bar1".some,
      releaseNotesUrl = uri"https://github.com/foo/bar1/blob/master/RELEASES.md".some
    )
    val obtained = updateInfoUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    val expected = List(CustomReleaseNotes(metadata.releaseNotesUrl.get))
    assertIO(obtained, expected)
  }

  test("findUpdateInfoUrls: repoUrl permanent redirect") {
    val metadata = DependencyMetadata.empty.copy(scmUrl = uri"https://github.com/foo/buz".some)
    val obtained = updateInfoUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  private val config = VCSCfg(
    VCSType.GitHub,
    uri"https://github.on-prem.com/",
    "",
    doNotFork = false,
    addLabels = false
  )
  private val githubOnPremVcsExtraAlg = new UpdateInfoUrlFinder[MockEff](config)

  test("findUpdateInfoUrls: on-prem, repoUrl not found") {
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/foo".some)
    val obtained = githubOnPremVcsExtraAlg.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  test("findUpdateInfoUrls: on-prem, repoUrl ok") {
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/bar".some)
    val obtained = githubOnPremVcsExtraAlg.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    val expected =
      List(VersionDiff(uri"https://github.on-prem.com/foo/bar/compare/v0.1.0...v0.2.0"))
    assertIO(obtained, expected)
  }

  test("findUpdateInfoUrls: on-prem, repoUrl permanent redirect") {
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/buz".some)
    val obtained = githubOnPremVcsExtraAlg.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  private val update = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single

  test("possibleVersionDiffs") {
    val onPremVCS = "https://github.onprem.io/"
    val onPremVCSUri = uri"https://github.onprem.io/"

    assertEquals(
      possibleVersionDiffs(
        GitHub,
        onPremVCSUri,
        uri"https://github.com/foo/bar",
        update.currentVersion,
        update.nextVersion
      ).map(_.url.renderString),
      List(
        "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
        "https://github.com/foo/bar/compare/1.2.0...1.2.3",
        "https://github.com/foo/bar/compare/release-1.2.0...release-1.2.3"
      )
    )

    // should canonicalize (drop last slash)
    assertEquals(
      possibleVersionDiffs(
        GitHub,
        onPremVCSUri,
        uri"https://github.com/foo/bar/",
        update.currentVersion,
        update.nextVersion
      ).map(_.url.renderString),
      List(
        "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
        "https://github.com/foo/bar/compare/1.2.0...1.2.3",
        "https://github.com/foo/bar/compare/release-1.2.0...release-1.2.3"
      )
    )

    assertEquals(
      possibleVersionDiffs(
        GitHub,
        onPremVCSUri,
        uri"https://gitlab.com/foo/bar",
        update.currentVersion,
        update.nextVersion
      ).map(_.url.renderString),
      List(
        "https://gitlab.com/foo/bar/compare/v1.2.0...v1.2.3",
        "https://gitlab.com/foo/bar/compare/1.2.0...1.2.3",
        "https://gitlab.com/foo/bar/compare/release-1.2.0...release-1.2.3"
      )
    )

    assertEquals(
      possibleVersionDiffs(
        GitHub,
        onPremVCSUri,
        uri"https://bitbucket.org/foo/bar",
        update.currentVersion,
        update.nextVersion
      ).map(_.url.renderString),
      List(
        "https://bitbucket.org/foo/bar/compare/v1.2.3..v1.2.0#diff",
        "https://bitbucket.org/foo/bar/compare/1.2.3..1.2.0#diff",
        "https://bitbucket.org/foo/bar/compare/release-1.2.3..release-1.2.0#diff"
      )
    )

    assertEquals(
      possibleVersionDiffs(
        GitHub,
        onPremVCSUri,
        uri"https://scalacenter.github.io/scalafix/",
        update.currentVersion,
        update.nextVersion
      ),
      List.empty
    )

    assertEquals(
      possibleVersionDiffs(
        GitHub,
        onPremVCSUri,
        onPremVCSUri.addPath("foo/bar"),
        update.currentVersion,
        update.nextVersion
      ).map(_.url.renderString),
      List(
        s"${onPremVCS}foo/bar/compare/v1.2.0...v1.2.3",
        s"${onPremVCS}foo/bar/compare/1.2.0...1.2.3",
        s"${onPremVCS}foo/bar/compare/release-1.2.0...release-1.2.3"
      )
    )
  }

  test("possibleUpdateInfoUrls: github.com") {
    val obtained = possibleUpdateInfoUrls(
      GitHub,
      uri"https://github.com",
      uri"https://github.com/foo/bar",
      update.currentVersion,
      update.nextVersion
    ).map(_.url.renderString)
    val expected = List(
      "https://github.com/foo/bar/releases/tag/v1.2.3",
      "https://github.com/foo/bar/releases/tag/1.2.3",
      "https://github.com/foo/bar/releases/tag/release-1.2.3",
      "https://github.com/foo/bar/blob/master/ReleaseNotes.md",
      "https://github.com/foo/bar/blob/master/ReleaseNotes.markdown",
      "https://github.com/foo/bar/blob/master/ReleaseNotes.rst",
      "https://github.com/foo/bar/blob/master/RELEASES.md",
      "https://github.com/foo/bar/blob/master/RELEASES.markdown",
      "https://github.com/foo/bar/blob/master/RELEASES.rst",
      "https://github.com/foo/bar/blob/master/Releases.md",
      "https://github.com/foo/bar/blob/master/Releases.markdown",
      "https://github.com/foo/bar/blob/master/Releases.rst",
      "https://github.com/foo/bar/blob/master/releases.md",
      "https://github.com/foo/bar/blob/master/releases.markdown",
      "https://github.com/foo/bar/blob/master/releases.rst",
      "https://github.com/foo/bar/blob/master/CHANGELOG.md",
      "https://github.com/foo/bar/blob/master/CHANGELOG.markdown",
      "https://github.com/foo/bar/blob/master/CHANGELOG.rst",
      "https://github.com/foo/bar/blob/master/Changelog.md",
      "https://github.com/foo/bar/blob/master/Changelog.markdown",
      "https://github.com/foo/bar/blob/master/Changelog.rst",
      "https://github.com/foo/bar/blob/master/changelog.md",
      "https://github.com/foo/bar/blob/master/changelog.markdown",
      "https://github.com/foo/bar/blob/master/changelog.rst",
      "https://github.com/foo/bar/blob/master/CHANGES.md",
      "https://github.com/foo/bar/blob/master/CHANGES.markdown",
      "https://github.com/foo/bar/blob/master/CHANGES.rst",
      "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
      "https://github.com/foo/bar/compare/1.2.0...1.2.3",
      "https://github.com/foo/bar/compare/release-1.2.0...release-1.2.3"
    )
    assertEquals(obtained, expected)
  }

  test("possibleUpdateInfoUrls: gitlab.com") {
    val obtained = possibleUpdateInfoUrls(
      GitHub,
      uri"https://github.com",
      uri"https://gitlab.com/foo/bar",
      update.currentVersion,
      update.nextVersion
    ).map(_.url.renderString)
    val expected =
      possibleReleaseNotesFilenames.map(name => s"https://gitlab.com/foo/bar/blob/master/$name") ++
        possibleChangelogFilenames.map(name => s"https://gitlab.com/foo/bar/blob/master/$name") ++
        List(
          "https://gitlab.com/foo/bar/compare/v1.2.0...v1.2.3",
          "https://gitlab.com/foo/bar/compare/1.2.0...1.2.3",
          "https://gitlab.com/foo/bar/compare/release-1.2.0...release-1.2.3"
        )
    assertEquals(obtained, expected)
  }

  test("possibleUpdateInfoUrls: on-prem gitlab") {
    val obtained = possibleUpdateInfoUrls(
      GitLab,
      uri"https://gitlab.on-prem.net",
      uri"https://gitlab.on-prem.net/foo/bar",
      update.currentVersion,
      update.nextVersion
    ).map(_.url.renderString)
    val expected = possibleReleaseNotesFilenames.map(name =>
      s"https://gitlab.on-prem.net/foo/bar/blob/master/$name"
    ) ++ possibleChangelogFilenames.map(name =>
      s"https://gitlab.on-prem.net/foo/bar/blob/master/$name"
    ) ++ List(
      "https://gitlab.on-prem.net/foo/bar/compare/v1.2.0...v1.2.3",
      "https://gitlab.on-prem.net/foo/bar/compare/1.2.0...1.2.3",
      "https://gitlab.on-prem.net/foo/bar/compare/release-1.2.0...release-1.2.3"
    )
    assertEquals(obtained, expected)
  }

  test("possibleUpdateInfoUrls: bitbucket.org") {
    val obtained = possibleUpdateInfoUrls(
      GitHub,
      uri"https://github.com",
      uri"https://bitbucket.org/foo/bar",
      update.currentVersion,
      update.nextVersion
    ).map(_.url.renderString)
    val expected =
      possibleReleaseNotesFilenames.map(name => s"https://bitbucket.org/foo/bar/master/$name") ++
        possibleChangelogFilenames.map(name => s"https://bitbucket.org/foo/bar/master/$name") ++
        List(
          "https://bitbucket.org/foo/bar/compare/v1.2.3..v1.2.0#diff",
          "https://bitbucket.org/foo/bar/compare/1.2.3..1.2.0#diff",
          "https://bitbucket.org/foo/bar/compare/release-1.2.3..release-1.2.0#diff"
        )
    assertEquals(obtained, expected)
  }

  test("possibleUpdateInfoUrls: homepage") {
    val obtained = possibleUpdateInfoUrls(
      GitHub,
      uri"https://github.com",
      uri"https://scalacenter.github.io/scalafix/",
      update.currentVersion,
      update.nextVersion
    ).map(_.url.renderString)
    assertEquals(obtained, List.empty)
  }
}
