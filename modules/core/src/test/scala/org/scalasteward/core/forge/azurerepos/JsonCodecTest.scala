/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
