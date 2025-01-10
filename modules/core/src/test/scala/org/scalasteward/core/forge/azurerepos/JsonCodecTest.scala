package org.scalasteward.core.forge.azurerepos

import io.circe.literal.*
import io.circe.syntax.*
import munit.FunSuite
import org.scalasteward.core.forge.data.NewPullRequestData
import org.scalasteward.core.git.Branch

class JsonCodecTest extends FunSuite {
  import JsonCodec.*

  private val data = NewPullRequestData(
    title = "Test MR title",
    body = "Test MR body",
    head = "source",
    base = Branch("main"),
    labels = Nil,
    assignees = Nil,
    reviewers = Nil
  )

  test("PullRequestPayload") {
    val obtained = PullRequestPayload.from(data).asJson
    val expected =
      json"""{
               "sourceRefName" : "refs/heads/source",
               "targetRefName" : "refs/heads/main",
               "title" : "Test MR title",
               "labels" : null,
               "description" : "Test MR body"
             }"""

    assertEquals(obtained, expected)
  }

  test("PullRequestPayload with labels") {
    val obtained = PullRequestPayload.from(data.copy(labels = List("foo", "bar"))).asJson
    val expected =
      json"""{
               "sourceRefName" : "refs/heads/source",
               "targetRefName" : "refs/heads/main",
               "title" : "Test MR title",
               "labels" : [ "foo", "bar" ],
               "description" : "Test MR body"
             }"""

    assertEquals(obtained, expected)
  }
}
