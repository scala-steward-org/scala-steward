package org.scalasteward.core.forge.github

import munit.FunSuite
import io.circe.literal._
import io.circe.syntax._
import org.scalasteward.core.data.{Repo, RepoData, UpdateData}
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.forge.data.NewPullRequestData._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.repoconfig.RepoConfig

class JsonCodecTest extends FunSuite {
  test("PullRequestPayload") {
    val updateData = UpdateData(
      RepoData(Repo("foo", "bar"), dummyRepoCache, RepoConfig.empty),
      Repo("scala-steward", "bar"),
      ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single,
      Branch("master"),
      dummySha1,
      Branch("update/logback-classic-1.2.3")
    )
    val pullRequestData = from(
      updateData,
      "scala-steward:update/logback-classic-1.2.3",
      labels = labelsFor(updateData.update)
    )

    val payload = PullRequestPayload.from(pullRequestData).asJson.spaces2
    val expected =
      raw"""|{
            |  "title" : "Update logback-classic to 1.2.3",
            |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.\n\nConfigure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.\n\nHave a fantastic day writing Scala!\n\n<details>\n<summary>Adjust future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [ { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" } ]\n```\nOr, add this to slow down future updates of this dependency:\n```\ndependencyOverrides = [{\n  pullRequests = { frequency = \"30 days\" },\n  dependency = { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" }\n}]\n```\n</details>\n\nlabels: library-update, early-semver-patch, semver-spec-patch, commit-count:0",
            |  "head" : "scala-steward:update/logback-classic-1.2.3",
            |  "base" : "master",
            |  "draft" : false
            |}""".stripMargin
    assertEquals(payload, expected)
  }

  test("GitHubAssignees") {
    val assignees = GitHubAssignees(List("foo", "bar")).asJson
    val expected =
      json"""{
        "assignees": [ "foo", "bar" ]
      }"""

    assertEquals(assignees, expected)
  }

  test("GitHubReviewers") {
    val reviewers = GitHubReviewers(List("foo", "bar")).asJson
    val expected =
      json"""{
        "reviewers": [ "foo", "bar" ]
      }"""

    assertEquals(reviewers, expected)
  }
}
