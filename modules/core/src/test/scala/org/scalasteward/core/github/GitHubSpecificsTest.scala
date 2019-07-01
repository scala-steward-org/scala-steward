package org.scalasteward.core.github

import org.scalasteward.core.model.Update
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.{FunSuite, Matchers}
import org.scalasteward.core.util.Nel

class GitHubSpecificsTest extends FunSuite with Matchers {
  val specifics = new GitHubSpecifics

  test("headForListingPullRequests") {
    specifics.headForListingPullRequests(
      Repo("scala-steward", "bar"),
      Update.Single("ch.qos.logback", "logback-classic", "1.2.0", Nel.of("1.2.3"))
    ) shouldBe "scala-steward/bar:update/logback-classic-1.2.3"
  }
}
