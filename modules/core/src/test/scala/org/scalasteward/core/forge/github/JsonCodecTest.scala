package org.scalasteward.core.forge.github

import io.circe.literal.*
import io.circe.syntax.*
import munit.FunSuite
import org.scalasteward.core.git.Branch

class JsonCodecTest extends FunSuite {
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
        "reviewers": [ "foo", "bar" ],
        "team_reviewers": []
      }"""

    assertEquals(reviewers, expected)
  }

  test("GitHubReviewers with team reviewers") {
    val reviewers = GitHubReviewers(List("foo", "bar", "scala-steward-org/scala-steward")).asJson
    val expected =
      json"""{
        "reviewers": [ "foo", "bar" ],
        "team_reviewers": [ "scala-steward" ]
      }"""

    assertEquals(reviewers, expected)
  }

  test("CreatePullRequestPayload") {
    val payload = CreatePullRequestPayload(
      title = "Update logback-classic to 1.2.3",
      body = "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3",
      head = "scala-steward:update/logback-classic-1.2.3",
      base = Branch("main"),
      draft = false
    ).asJson
    val expected =
      json"""{
        "title" : "Update logback-classic to 1.2.3",
        "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3",
        "head" : "scala-steward:update/logback-classic-1.2.3",
        "base" : "main",
        "draft" : false
      }"""
    assertEquals(payload, expected)
  }
}
