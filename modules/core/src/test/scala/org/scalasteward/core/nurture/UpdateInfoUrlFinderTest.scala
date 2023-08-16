package org.scalasteward.core.nurture

import cats.syntax.all._
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.scalasteward.core.application.Config.ForgeCfg
import org.scalasteward.core.coursier.DependencyMetadata
import org.scalasteward.core.data.Version
import org.scalasteward.core.forge.ForgeType
import org.scalasteward.core.forge.ForgeType._
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.nurture.UpdateInfoUrl._
import org.scalasteward.core.nurture.UpdateInfoUrlFinder._

class UpdateInfoUrlFinderTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  private val state = MockState.empty.copy(clientResponses = HttpApp {
    case HEAD -> Root / "foo" / "bar" / "README.md"                        => Ok()
    case HEAD -> Root / "foo" / "bar" / "compare" / "v0.1.0...v0.2.0"      => Ok()
    case HEAD -> Root / "foo" / "bar1" / "blob" / "master" / "RELEASES.md" => Ok()
    case HEAD -> Root / "foo" / "buz" / "compare" / "v0.1.0...v0.2.0"      => PermanentRedirect()
    case HEAD -> Root / "foo" / "bar2" / "releases" / "tag" / "v0.2.0"     => Ok()
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

  test("findUpdateInfoUrls: GitHubReleaseNotes and CustomReleaseNotes with the same URL") {
    val metadata = DependencyMetadata.empty.copy(
      scmUrl = uri"https://github.com/foo/bar2".some,
      releaseNotesUrl = uri"https://github.com/foo/bar2/releases/tag/v0.2.0".some
    )
    val obtained = updateInfoUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    val expected = List(GitHubReleaseNotes(metadata.releaseNotesUrl.get))
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

  implicit private val config: ForgeCfg = ForgeCfg(
    ForgeType.GitHub,
    uri"https://github.on-prem.com/",
    "",
    doNotFork = false,
    addLabels = false
  )
  private val onPremUpdateUrlFinder = new UpdateInfoUrlFinder[MockEff]

  test("findUpdateInfoUrls: on-prem, repoUrl not found") {
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/foo".some)
    val obtained = onPremUpdateUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  test("findUpdateInfoUrls: on-prem, repoUrl ok") {
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/bar".some)
    val obtained = onPremUpdateUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    val expected =
      List(VersionDiff(uri"https://github.on-prem.com/foo/bar/compare/v0.1.0...v0.2.0"))
    assertIO(obtained, expected)
  }

  test("findUpdateInfoUrls: on-prem, repoUrl permanent redirect") {
    val metadata =
      DependencyMetadata.empty.copy(scmUrl = uri"https://github.on-prem.com/foo/buz".some)
    val obtained = onPremUpdateUrlFinder.findUpdateInfoUrls(metadata, v1, v2).runA(state)
    assertIO(obtained, List.empty)
  }

  test("possibleVersionDiffs") {
    val onPremForgeUrl = uri"https://github.onprem.io/"

    assertEquals(
      possibleVersionDiffs(GitHub, uri"https://github.com/foo/bar", v1, v2)
        .map(_.url.renderString),
      List(
        s"https://github.com/foo/bar/compare/v$v1...v$v2",
        s"https://github.com/foo/bar/compare/$v1...$v2",
        s"https://github.com/foo/bar/compare/release-$v1...release-$v2"
      )
    )

    // should canonicalize (drop last slash)
    assertEquals(
      possibleVersionDiffs(GitHub, uri"https://github.com/foo/bar/", v1, v2)
        .map(_.url.renderString),
      List(
        s"https://github.com/foo/bar/compare/v$v1...v$v2",
        s"https://github.com/foo/bar/compare/$v1...$v2",
        s"https://github.com/foo/bar/compare/release-$v1...release-$v2"
      )
    )

    assertEquals(
      possibleVersionDiffs(GitHub, uri"https://gitlab.com/foo/bar", v1, v2)
        .map(_.url.renderString),
      List(
        s"https://gitlab.com/foo/bar/compare/v$v1...v$v2",
        s"https://gitlab.com/foo/bar/compare/$v1...$v2",
        s"https://gitlab.com/foo/bar/compare/release-$v1...release-$v2"
      )
    )

    assertEquals(
      possibleVersionDiffs(Bitbucket, uri"https://bitbucket.org/foo/bar", v1, v2)
        .map(_.url.renderString),
      List(
        s"https://bitbucket.org/foo/bar/compare/v$v2..v$v1#diff",
        s"https://bitbucket.org/foo/bar/compare/$v2..$v1#diff",
        s"https://bitbucket.org/foo/bar/compare/release-$v2..release-$v1#diff"
      )
    )

    assertEquals(
      possibleVersionDiffs(GitHub, onPremForgeUrl.addPath("foo/bar"), v1, v2)
        .map(_.url.renderString),
      List(
        s"${onPremForgeUrl}foo/bar/compare/v$v1...v$v2",
        s"${onPremForgeUrl}foo/bar/compare/$v1...$v2",
        s"${onPremForgeUrl}foo/bar/compare/release-$v1...release-$v2"
      )
    )

    assertEquals(
      possibleVersionDiffs(AzureRepos, onPremForgeUrl.addPath("foo/bar"), v1, v2)
        .map(_.url.renderString),
      List(
        s"${onPremForgeUrl}foo/bar/branchCompare?baseVersion=GTv$v1&targetVersion=GTv$v2",
        s"${onPremForgeUrl}foo/bar/branchCompare?baseVersion=GT$v1&targetVersion=GT$v2",
        s"${onPremForgeUrl}foo/bar/branchCompare?baseVersion=GTrelease-$v1&targetVersion=GTrelease-$v2"
      )
    )
  }

  test("possibleUpdateInfoUrls: github.com") {
    val obtained = possibleUpdateInfoUrls(
      GitHub,
      uri"https://github.com/foo/bar",
      v1,
      v2
    ).map(_.url.renderString)
    val expected = List(
      s"https://github.com/foo/bar/releases/tag/v$v2",
      s"https://github.com/foo/bar/releases/tag/$v2",
      s"https://github.com/foo/bar/releases/tag/release-$v2",
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
      s"https://github.com/foo/bar/compare/v$v1...v$v2",
      s"https://github.com/foo/bar/compare/$v1...$v2",
      s"https://github.com/foo/bar/compare/release-$v1...release-$v2"
    )
    assertEquals(obtained, expected)
  }

  test("possibleUpdateInfoUrls: gitlab.com") {
    val obtained = possibleUpdateInfoUrls(
      GitLab,
      uri"https://gitlab.com/foo/bar",
      v1,
      v2
    ).map(_.url.renderString)
    val expected =
      possibleReleaseNotesFilenames.map(name => s"https://gitlab.com/foo/bar/blob/master/$name") ++
        possibleChangelogFilenames.map(name => s"https://gitlab.com/foo/bar/blob/master/$name") ++
        List(
          s"https://gitlab.com/foo/bar/compare/v$v1...v$v2",
          s"https://gitlab.com/foo/bar/compare/$v1...$v2",
          s"https://gitlab.com/foo/bar/compare/release-$v1...release-$v2"
        )
    assertEquals(obtained, expected)
  }

  test("possibleUpdateInfoUrls: on-prem gitlab") {
    val obtained = possibleUpdateInfoUrls(
      GitLab,
      uri"https://gitlab.on-prem.net/foo/bar",
      v1,
      v2
    ).map(_.url.renderString)
    val expected = possibleReleaseNotesFilenames.map(name =>
      s"https://gitlab.on-prem.net/foo/bar/blob/master/$name"
    ) ++ possibleChangelogFilenames.map(name =>
      s"https://gitlab.on-prem.net/foo/bar/blob/master/$name"
    ) ++ List(
      s"https://gitlab.on-prem.net/foo/bar/compare/v$v1...v$v2",
      s"https://gitlab.on-prem.net/foo/bar/compare/$v1...$v2",
      s"https://gitlab.on-prem.net/foo/bar/compare/release-$v1...release-$v2"
    )
    assertEquals(obtained, expected)
  }

  test("possibleUpdateInfoUrls: bitbucket.org") {
    val obtained = possibleUpdateInfoUrls(
      Bitbucket,
      uri"https://bitbucket.org/foo/bar/",
      v1,
      v2
    ).map(_.url.renderString)
    val expected =
      possibleReleaseNotesFilenames.map(name =>
        s"https://bitbucket.org/foo/bar/src/master/$name"
      ) ++
        possibleChangelogFilenames.map(name => s"https://bitbucket.org/foo/bar/src/master/$name") ++
        List(
          s"https://bitbucket.org/foo/bar/compare/v$v2..v$v1#diff",
          s"https://bitbucket.org/foo/bar/compare/$v2..$v1#diff",
          s"https://bitbucket.org/foo/bar/compare/release-$v2..release-$v1#diff"
        )
    assertEquals(obtained, expected)
  }

  test("possibleUpdateInfoUrls: on-prem Bitbucket Server") {
    val forgeUrl = uri"https://bitbucket-server.on-prem.com"
    val repoUrl = forgeUrl / "foo" / "bar"
    val obtained = possibleUpdateInfoUrls(BitbucketServer, repoUrl, v1, v2).map(_.url)
    val expected = repoUrl / "browse" / "ReleaseNotes.md"
    assert(clue(obtained).contains(expected))
  }
}
