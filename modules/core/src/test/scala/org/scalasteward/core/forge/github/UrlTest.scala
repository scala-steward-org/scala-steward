package org.scalasteward.core.forge.github

import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch
import org.scalasteward.core.forge.data.PullRequestNumber

class UrlTest extends FunSuite {
  private val url = new Url(uri"https://api.github.com")
  import url._

  private val repo = Repo("fthomas", "refined")
  private val branch = Branch("master")

  test("branches") {
    assertEquals(
      branches(repo, branch).toString,
      "https://api.github.com/repos/fthomas/refined/branches/master"
    )
  }

  test("forks") {
    assertEquals(forks(repo).toString, "https://api.github.com/repos/fthomas/refined/forks")
  }

  test("listPullRequests") {
    assertEquals(
      listPullRequests(
        repo,
        "scala-steward:update/fs2-core-1.0.0",
        Branch("series/0.6.x")
      ).toString,
      "https://api.github.com/repos/fthomas/refined/pulls?head=scala-steward%3Aupdate/fs2-core-1.0.0&base=series/0.6.x&state=all"
    )
  }

  test("pulls") {
    assertEquals(pulls(repo).toString, "https://api.github.com/repos/fthomas/refined/pulls")
  }

  test("repos") {
    assertEquals(repos(repo).toString, "https://api.github.com/repos/fthomas/refined")
  }

  test("assignees") {
    assertEquals(
      assignees(repo, PullRequestNumber(1)).toString,
      "https://api.github.com/repos/fthomas/refined/issues/1/assignees"
    )
  }

  test("reviewers") {
    assertEquals(
      reviewers(repo, PullRequestNumber(1)).toString,
      "https://api.github.com/repos/fthomas/refined/pulls/1/requested_reviewers"
    )
  }
}
