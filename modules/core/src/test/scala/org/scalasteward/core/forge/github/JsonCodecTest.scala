package org.scalasteward.core.forge.github

import munit.FunSuite
import io.circe.literal._
import io.circe.syntax._

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
}
