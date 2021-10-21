package org.scalasteward.core.edit.scalafix

import io.circe.config.parser
import munit.FunSuite
import org.scalasteward.core.util.Nel

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
    val obtained = migration.map(_.commitMessage(Right(())).toNel)
    val expected = Nel.of(
      "Applied Scalafix rule(s) github:typelevel/cats/Cats_v2_2_0?sha=v2.2.0",
      "See https://github.com/typelevel/cats/blob/v2.2.0/scalafix/README.md#migration-to-cats-v220 for details",
      "Co-authored-by: Jane Doe <jane@example.com>"
    )
    assertEquals(obtained, Right(expected))
  }
}
