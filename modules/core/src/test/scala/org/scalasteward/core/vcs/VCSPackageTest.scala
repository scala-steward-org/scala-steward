package org.scalasteward.core.vcs

import org.scalasteward.core.application.SupportedVCS.GitHub
import org.scalasteward.core.application.SupportedVCS.Gitlab
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.data.Update

import org.scalatest.{FunSuite, Matchers}

class VCSPackageTest extends FunSuite with Matchers {
  val repo = Repo("foo", "bar")
  val update = Update.Single("ch.qos.logback", "logback-classic", "1.2.0", Nel.of("1.2.3"))

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
}
