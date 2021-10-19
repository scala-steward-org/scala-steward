package org.scalasteward.core.edit.scalafix

import munit.FunSuite
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.git.Author
import org.scalasteward.core.util.Nel

class ScalafixMigrationTest extends FunSuite {
  test("commitMessage") {
    val migration = ScalafixMigration(
      GroupId("co.fs2"),
      Nel.of("fs2-core"),
      Version("1.0.0"),
      Nel.of("github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"),
      authors = Some(Nel.of(Author("Jane Doe", "jane@example.com", None)))
    )
    val expected = Nel.of(
      "Applied Scalafix rule(s) github:functional-streams-for-scala/fs2/v1?sha=v1.0.5",
      "Co-authored-by: Jane Doe <jane@example.com>"
    )
    assertEquals(migration.commitMessage(Right(())), expected)
  }
}
