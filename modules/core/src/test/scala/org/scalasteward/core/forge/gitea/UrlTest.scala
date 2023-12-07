package org.scalasteward.core.forge.gitea

import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.data.PullRequestNumber
import org.scalasteward.core.git.Branch

class UrlTest extends FunSuite {
  val url = new Url(uri"https://git.example.com/api/v1")

  val repo = Repo("scala-steward-org", "scala-steward")
  val branch = Branch("main")

  test("repos") {
    assertEquals(
      url.repos(repo).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward"
    )
  }

  test("branch") {
    assertEquals(
      url.repoBranch(repo, branch).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward/branches/main"
    )
  }

  test("forks") {
    assertEquals(
      url.forks(repo).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward/forks"
    )
  }

  test("pulls") {
    assertEquals(
      url.pulls(repo).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward/pulls"
    )
  }

  test("pull") {
    assertEquals(
      url.pull(repo, PullRequestNumber(1)).toString(),
      "https://git.example.com/api/v1/repos/scala-steward-org/scala-steward/pulls/1"
    )
  }
}
