package org.scalasteward.core.vcs.gitlab

import io.circe.literal._
import io.circe.syntax._
import munit.FunSuite
import org.scalasteward.core.git.Branch
import org.scalasteward.core.vcs.data.NewPullRequestData
import org.scalasteward.core.vcs.gitlab.GitLabJsonCodec._

class MergeRequestPayloadTest extends FunSuite {
  private val master = Branch("master")
  private val data = NewPullRequestData(
    "Test MR title",
    "Test MR body",
    "source",
    master
  )
  private val id = "123"
  private val projectId = 321L

  test("asJson") {
    val obtained = MergeRequestPayload(id, projectId, data).asJson
    val expected =
      json"""{
               "id" : "123",
               "title" : "Test MR title",
               "description" : "Test MR body",
               "target_project_id" : 321,
               "source_branch" : "source",
               "target_branch" : "master"
             }"""
    assertEquals(obtained, expected)
  }

  test("asJson for draft MR") {
    val obtained = MergeRequestPayload(id, projectId, data.copy(draft = true)).asJson
    val expected =
      json"""{
               "id" : "123",
               "title" : "Draft: Test MR title",
               "description" : "Test MR body",
               "target_project_id" : 321,
               "source_branch" : "source",
               "target_branch" : "master"
             }"""
    assertEquals(obtained, expected)
  }
}
