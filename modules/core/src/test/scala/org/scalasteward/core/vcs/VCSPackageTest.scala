package org.scalasteward.core.vcs

import org.scalasteward.core.application.SupportedVCS.GitHub
import org.scalasteward.core.application.SupportedVCS.Gitlab
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.data.Update

import org.scalatest.{FunSuite, Matchers}

class BranchOutTest extends FunSuite with Matchers {
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

  test("createCompareUrl") {
    createCompareUrl("https://github.com/foo/bar", update) shouldBe Some(
      "https://github.com/foo/bar/compare/v1.2.0...v1.2.3"
    )
    // should canonicalize (drop last slash)
    createCompareUrl("https://github.com/foo/bar/", update) shouldBe Some(
      "https://github.com/foo/bar/compare/v1.2.0...v1.2.3"
    )

    createCompareUrl("https://gitlab.com/foo/bar", update) shouldBe Some(
      "https://gitlab.com/foo/bar/compare/v1.2.0...v1.2.3"
    )
    createCompareUrl("https://bitbucket.org/foo/bar", update) shouldBe Some(
      "https://bitbucket.org/foo/bar/compare/1.2.3..1.2.0#diff"
    )

    createCompareUrl("https://scalacenter.github.io/scalafix/", update) shouldBe None
  }
}
