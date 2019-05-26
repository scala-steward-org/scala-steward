package org.scalasteward.core.github

import org.http4s.Uri
import org.scalasteward.core.git.Branch
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.{FunSuite, Matchers}

class UrlTest extends FunSuite with Matchers {
  val url = new Url(Uri.uri("https://api.github.com"))
  import url._

  val repo = Repo("fthomas", "refined")
  val branch = Branch("master")

  test("branches") {
    branches(repo, branch).toString shouldBe
      "https://api.github.com/repos/fthomas/refined/branches/master"
  }

  test("forks") {
    forks(repo).toString shouldBe
      "https://api.github.com/repos/fthomas/refined/forks"
  }

  test("listPullRequests") {
    listPullRequests(repo, "scala-steward:update/fs2-core-1.0.0", Branch("series/0.6.x")).toString shouldBe
      "https://api.github.com/repos/fthomas/refined/pulls?head=scala-steward%3Aupdate/fs2-core-1.0.0&base=series/0.6.x&state=all"
  }

  test("pulls") {
    pulls(repo).toString shouldBe
      "https://api.github.com/repos/fthomas/refined/pulls"
  }

  test("repos") {
    repos(repo).toString shouldBe
      "https://api.github.com/repos/fthomas/refined"
  }
}
