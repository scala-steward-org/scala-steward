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

package org.scalasteward.core.edit.scalafix

import io.circe.config.parser
import munit.FunSuite
import org.scalasteward.core.edit.scalafix.ScalafixMigration.{ExecutionOrder, Target}
import org.scalasteward.core.git.{Author, CommitMsg}

class ScalafixMigrationTest extends FunSuite {
  test("commitMessage") {
    val migration = parser.decode[ScalafixMigration](
      """|{
         |  groupId: "org.typelevel",
         |  artifactIds: ["cats-core"],
         |  newVersion: "2.2.0",
         |  rewriteRules: ["github:typelevel/cats/Cats_v2_2_0?sha=v2.2.0"],
         |  doc: "https://github.com/typelevel/cats/blob/v2.2.0/scalafix/README.md#migration-to-cats-v220",
         |  scalacOptions: ["-P:semanticdb:synthetics:on"],
         |  authors: ["Jane Doe <jane@example.com>"]
         |}""".stripMargin
    )
    val obtained = migration.map(_.commitMessage(Right(())))
    val expected = CommitMsg(
      title = "Applied Scalafix rule(s) github:typelevel/cats/Cats_v2_2_0?sha=v2.2.0",
      body = List(
        "See https://github.com/typelevel/cats/blob/v2.2.0/scalafix/README.md#migration-to-cats-v220 for details"
      ),
      coAuthoredBy = List(Author("Jane Doe", "jane@example.com"))
    )
    assertEquals(obtained, Right(expected))
  }

  test("decode unknown executionOrder") {
    assert(io.circe.parser.decode[ExecutionOrder](""""foo"""").isLeft)
  }

  test("decode unknown target") {
    assert(io.circe.parser.decode[Target](""""foo"""").isLeft)
  }
}
