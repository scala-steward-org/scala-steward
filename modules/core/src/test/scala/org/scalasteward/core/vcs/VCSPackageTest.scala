package org.scalasteward.core.vcs

import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.VCSType.{GitHub, GitLab}
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.git.Branch

class VCSPackageTest extends FunSuite {
  val repo: Repo = Repo("foo", "bar")
  val branch: Branch = Branch("custom")
  val update: Update.Single =
    Update.Single("ch.qos.logback" % "logback-classic" % "1.2.0", Nel.of("1.2.3"))

  test("listingBranch") {
    assertEquals(listingBranch(GitHub, repo, update, Some(branch)), "foo/bar:update/custom/logback-classic-1.2.3")
    assertEquals(listingBranch(GitLab, repo, update, Some(branch)), "update/custom/logback-classic-1.2.3")
    assertEquals(listingBranch(GitHub, repo, update,None), "foo/bar:update/logback-classic-1.2.3")
    assertEquals(listingBranch(GitLab, repo, update,None), "update/logback-classic-1.2.3")
  }

  test("createBranch") {
    assertEquals(createBranch(GitHub, repo, update, Some(branch)), "foo:update/custom/logback-classic-1.2.3")
    assertEquals(createBranch(GitLab, repo, update, Some(branch)), "update/custom/logback-classic-1.2.3")
    assertEquals(createBranch(GitHub, repo, update, None), "foo:update/logback-classic-1.2.3")
    assertEquals(createBranch(GitLab, repo, update, None), "update/logback-classic-1.2.3")
  }

  test("possibleCompareUrls") {
    val onPremVCS = "https://github.onprem.io/"
    val onPremVCSUri = uri"https://github.onprem.io/"

    assertEquals(
      possibleCompareUrls(
        GitHub,
        onPremVCSUri,
        uri"https://github.com/foo/bar",
        update
      ).map(_.url.renderString),
      List(
        "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
        "https://github.com/foo/bar/compare/1.2.0...1.2.3",
        "https://github.com/foo/bar/compare/release-1.2.0...release-1.2.3"
      )
    )

    // should canonicalize (drop last slash)
    assertEquals(
      possibleCompareUrls(
        GitHub,
        onPremVCSUri,
        uri"https://github.com/foo/bar/",
        update
      ).map(_.url.renderString),
      List(
        "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
        "https://github.com/foo/bar/compare/1.2.0...1.2.3",
        "https://github.com/foo/bar/compare/release-1.2.0...release-1.2.3"
      )
    )

    assertEquals(
      possibleCompareUrls(
        GitHub,
        onPremVCSUri,
        uri"https://gitlab.com/foo/bar",
        update
      ).map(_.url.renderString),
      List(
        "https://gitlab.com/foo/bar/compare/v1.2.0...v1.2.3",
        "https://gitlab.com/foo/bar/compare/1.2.0...1.2.3",
        "https://gitlab.com/foo/bar/compare/release-1.2.0...release-1.2.3"
      )
    )

    assertEquals(
      possibleCompareUrls(
        GitHub,
        onPremVCSUri,
        uri"https://bitbucket.org/foo/bar",
        update
      ).map(_.url.renderString),
      List(
        "https://bitbucket.org/foo/bar/compare/v1.2.3..v1.2.0#diff",
        "https://bitbucket.org/foo/bar/compare/1.2.3..1.2.0#diff",
        "https://bitbucket.org/foo/bar/compare/release-1.2.3..release-1.2.0#diff"
      )
    )

    assertEquals(
      possibleCompareUrls(
        GitHub,
        onPremVCSUri,
        uri"https://scalacenter.github.io/scalafix/",
        update
      ),
      List.empty
    )

    assertEquals(
      possibleCompareUrls(
        GitHub,
        onPremVCSUri,
        onPremVCSUri.addPath("foo/bar"),
        update
      ).map(_.url.renderString),
      List(
        s"${onPremVCS}foo/bar/compare/v1.2.0...v1.2.3",
        s"${onPremVCS}foo/bar/compare/1.2.0...1.2.3",
        s"${onPremVCS}foo/bar/compare/release-1.2.0...release-1.2.3"
      )
    )
  }

  test("possibleChangelogUrls: github.com") {
    val obtained = possibleReleaseRelatedUrls(
      GitHub,
      uri"https://github.com",
      uri"https://github.com/foo/bar",
      update
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

  test("possibleChangelogUrls: gitlab.com") {
    val obtained = possibleReleaseRelatedUrls(
      GitHub,
      uri"https://github.com",
      uri"https://gitlab.com/foo/bar",
      update
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

  test("possibleChangelogUrls: on-prem gitlab") {
    val obtained = possibleReleaseRelatedUrls(
      GitLab,
      uri"https://gitlab.on-prem.net",
      uri"https://gitlab.on-prem.net/foo/bar",
      update
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

  test("possibleChangelogUrls: bitbucket.org") {
    val obtained = possibleReleaseRelatedUrls(
      GitHub,
      uri"https://github.com",
      uri"https://bitbucket.org/foo/bar",
      update
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

  test("possibleChangelogUrls: homepage") {
    val obtained = possibleReleaseRelatedUrls(
      GitHub,
      uri"https://github.com",
      uri"https://scalacenter.github.io/scalafix/",
      update
    ).map(_.url.renderString)
    assertEquals(obtained, List.empty)
  }
}
