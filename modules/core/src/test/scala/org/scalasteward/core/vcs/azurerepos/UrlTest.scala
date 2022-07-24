package org.scalasteward.core.vcs.azurerepos

import org.http4s.syntax.literals._
import munit.FunSuite
import org.scalasteward.core.git.Branch
import org.scalasteward.core.vcs.data.{PullRequestNumber, Repo}

class UrlTest extends FunSuite {
  private val url = new Url(uri"https://dev.azure.com", "my-azure-org")
  import url._

  private val repo = Repo("scala-steward-org", "scala-steward")
  private val branch = Branch("/refs/heads/main")

  test("getRepo") {
    assertEquals(
      getRepo(repo).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward?api-version=7.1-preview.1&includeParent=true"
    )
  }

  test("pullRequests") {
    assertEquals(
      pullRequests(repo).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/pullrequests?api-version=7.1-preview.1"
    )
  }

  test("getBranch") {
    assertEquals(
      getBranch(repo, branch).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/stats%2Fbranches?api-version=7.1-preview.1&name=main"
    )
  }

  test("listPullRequests") {
    assertEquals(
      listPullRequests(repo, "update/sbt-1.7.1", Branch("main")).toString(),
      "https://dev.azure.com/my-azure-org/scala-steward-org/_apis%2Fgit%2Frepositories/scala-steward/pullrequests" +
        "?api-version=7.1-preview.1&searchCriteria.sourceRefName=update/sbt-1.7.1&searchCriteria.targetRefName=main"
    )
  }

  test("closePullRequest") {
    assertEquals(
      closePullRequest(repo, PullRequestNumber(1221)).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/pullrequests/1221?api-version=7.1-preview.1"
    )
  }

  test("commentPullRequest") {
    assertEquals(
      commentPullRequest(repo, PullRequestNumber(42)).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/pullrequests/42/threads?api-version=7.1-preview.1"
    )
  }

  test("labelPullRequest") {
    assertEquals(
      labelPullRequest(repo, PullRequestNumber(42)).toString,
      "https://dev.azure.com/my-azure-org/scala-steward-org/" +
        "_apis%2Fgit%2Frepositories/scala-steward/pullrequests/42/labels?api-version=7.1-preview.1"
    )
  }
}
