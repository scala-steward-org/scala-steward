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
