package org.scalasteward.core.vcs

import org.scalasteward.core.application.SupportedVCS.{GitHub, Gitlab}
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class VCSPackageTest extends AnyFunSuite with Matchers {
  val repo = Repo("foo", "bar")
  val update = Update.Single(GroupId("ch.qos.logback"), "logback-classic", "1.2.0", Nel.of("1.2.3"))

  test("listingBranch") {
    listingBranch(GitHub, repo, update) shouldBe "foo/bar:update/logback-classic-1.2.3"
    listingBranch(Gitlab, repo, update) shouldBe "update/logback-classic-1.2.3"
  }

  test("createBranch") {
    createBranch(GitHub, repo, update) shouldBe "foo:update/logback-classic-1.2.3"
    createBranch(Gitlab, repo, update) shouldBe "update/logback-classic-1.2.3"
  }

  test("possibleCompareUrls") {
    possibleCompareUrls("https://github.com/foo/bar", update) shouldBe List(
      "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
      "https://github.com/foo/bar/compare/1.2.0...1.2.3"
    )
    // should canonicalize (drop last slash)
    possibleCompareUrls("https://github.com/foo/bar/", update) shouldBe List(
      "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
      "https://github.com/foo/bar/compare/1.2.0...1.2.3"
    )

    possibleCompareUrls("https://gitlab.com/foo/bar", update) shouldBe List(
      "https://gitlab.com/foo/bar/compare/v1.2.0...v1.2.3",
      "https://gitlab.com/foo/bar/compare/1.2.0...1.2.3"
    )
    possibleCompareUrls("https://bitbucket.org/foo/bar", update) shouldBe List(
      "https://bitbucket.org/foo/bar/compare/v1.2.3..v1.2.0#diff",
      "https://bitbucket.org/foo/bar/compare/1.2.3..1.2.0#diff"
    )

    possibleCompareUrls("https://scalacenter.github.io/scalafix/", update) shouldBe List()
  }

  test("possibleReleaseNoteFiles") {
    // blob/<branch>
    possibleReleaseNoteFiles("https://github.com/foo/bar", update) shouldBe List(
      "https://github.com/foo/bar/blob/master/CHANGELOG.md",
      "https://github.com/foo/bar/blob/master/CHANGELOG.markdown",
      "https://github.com/foo/bar/blob/master/CHANGELOG.rst",
      "https://github.com/foo/bar/blob/master/Changelog.md",
      "https://github.com/foo/bar/blob/master/Changelog.markdown",
      "https://github.com/foo/bar/blob/master/Changelog.rst",
      "https://github.com/foo/bar/blob/master/changelog.md",
      "https://github.com/foo/bar/blob/master/changelog.markdown",
      "https://github.com/foo/bar/blob/master/changelog.rst",
      "https://github.com/foo/bar/blob/master/RELEASES.md",
      "https://github.com/foo/bar/blob/master/RELEASES.markdown",
      "https://github.com/foo/bar/blob/master/RELEASES.rst",
      "https://github.com/foo/bar/blob/master/Releases.md",
      "https://github.com/foo/bar/blob/master/Releases.markdown",
      "https://github.com/foo/bar/blob/master/Releases.rst",
      "https://github.com/foo/bar/blob/master/releases.md",
      "https://github.com/foo/bar/blob/master/releases.markdown",
      "https://github.com/foo/bar/blob/master/releases.rst",
      "https://github.com/foo/bar/releases/tag/1.2.3",
      "https://github.com/foo/bar/releases/tag/v1.2.3"
    )

    // blob/<branch>
    possibleReleaseNoteFiles("https://gitlab.com/foo/bar", update) shouldBe List(
      "https://gitlab.com/foo/bar/blob/master/CHANGELOG.md",
      "https://gitlab.com/foo/bar/blob/master/CHANGELOG.markdown",
      "https://gitlab.com/foo/bar/blob/master/CHANGELOG.rst",
      "https://gitlab.com/foo/bar/blob/master/Changelog.md",
      "https://gitlab.com/foo/bar/blob/master/Changelog.markdown",
      "https://gitlab.com/foo/bar/blob/master/Changelog.rst",
      "https://gitlab.com/foo/bar/blob/master/changelog.md",
      "https://gitlab.com/foo/bar/blob/master/changelog.markdown",
      "https://gitlab.com/foo/bar/blob/master/changelog.rst",
      "https://gitlab.com/foo/bar/blob/master/RELEASES.md",
      "https://gitlab.com/foo/bar/blob/master/RELEASES.markdown",
      "https://gitlab.com/foo/bar/blob/master/RELEASES.rst",
      "https://gitlab.com/foo/bar/blob/master/Releases.md",
      "https://gitlab.com/foo/bar/blob/master/Releases.markdown",
      "https://gitlab.com/foo/bar/blob/master/Releases.rst",
      "https://gitlab.com/foo/bar/blob/master/releases.md",
      "https://gitlab.com/foo/bar/blob/master/releases.markdown",
      "https://gitlab.com/foo/bar/blob/master/releases.rst"
    )

    // just <branch>
    possibleReleaseNoteFiles("https://bitbucket.org/foo/bar", update) shouldBe List(
      "https://bitbucket.org/foo/bar/master/CHANGELOG.md",
      "https://bitbucket.org/foo/bar/master/CHANGELOG.markdown",
      "https://bitbucket.org/foo/bar/master/CHANGELOG.rst",
      "https://bitbucket.org/foo/bar/master/Changelog.md",
      "https://bitbucket.org/foo/bar/master/Changelog.markdown",
      "https://bitbucket.org/foo/bar/master/Changelog.rst",
      "https://bitbucket.org/foo/bar/master/changelog.md",
      "https://bitbucket.org/foo/bar/master/changelog.markdown",
      "https://bitbucket.org/foo/bar/master/changelog.rst",
      "https://bitbucket.org/foo/bar/master/RELEASES.md",
      "https://bitbucket.org/foo/bar/master/RELEASES.markdown",
      "https://bitbucket.org/foo/bar/master/RELEASES.rst",
      "https://bitbucket.org/foo/bar/master/Releases.md",
      "https://bitbucket.org/foo/bar/master/Releases.markdown",
      "https://bitbucket.org/foo/bar/master/Releases.rst",
      "https://bitbucket.org/foo/bar/master/releases.md",
      "https://bitbucket.org/foo/bar/master/releases.markdown",
      "https://bitbucket.org/foo/bar/master/releases.rst"
    )

    // Empty for homepage
    possibleReleaseNoteFiles("https://scalacenter.github.io/scalafix/", update) shouldBe List()
  }
}
