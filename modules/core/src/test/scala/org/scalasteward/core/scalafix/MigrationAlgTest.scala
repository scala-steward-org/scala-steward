package org.scalasteward.core.scalafix

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{GroupId, Update, Version}
import org.scalasteward.core.mock.MockContext.context.migrationAlg
import org.scalasteward.core.util.Nel

class MigrationAlgTest extends FunSuite {
  test("findMigrations") {
    val update = Update.Single("org.typelevel" % "cats-core" % "2.1.0", Nel.of("2.2.0"))
    val migrations = migrationAlg.findMigrations(update)
    val expected = List(
      Migration(
        GroupId("org.typelevel"),
        Nel.of("cats-core"),
        Version("2.2.0"),
        Nel.of("github:typelevel/cats/Cats_v2_2_0?sha=v2.2.0"),
        Some(
          "https://github.com/typelevel/cats/blob/v2.2.0/scalafix/README.md#migration-to-cats-v220"
        ),
        Some(Nel.of("-P:semanticdb:synthetics:on"))
      )
    )
    assertEquals(migrations, expected)
  }
}
