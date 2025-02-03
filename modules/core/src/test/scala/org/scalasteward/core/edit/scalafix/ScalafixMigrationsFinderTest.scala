package org.scalasteward.core.edit.scalafix

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.mock.MockContext.context.scalafixMigrationsFinder
import org.scalasteward.core.util.Nel

class ScalafixMigrationsFinderTest extends FunSuite {
  test("findMigrations") {
    val update = ("org.typelevel".g % "cats-core".a % "2.1.0" %> "2.2.0").single
    val obtained = scalafixMigrationsFinder.findMigrations(update)
    val expected = (
      List(
        ScalafixMigration(
          GroupId("org.typelevel"),
          Nel.of("cats-core"),
          Version("2.2.0"),
          Nel.of("github:typelevel/cats/Cats_v2_2_0?sha=v2.2.0"),
          Some(
            "https://github.com/typelevel/cats/blob/v2.2.0/scalafix/README.md#migration-to-cats-v220"
          ),
          Some(Nel.of("-P:semanticdb:synthetics:on"))
        )
      ),
      List()
    )
    assertEquals(obtained, expected)
  }

  test("findMigrations: pattern must match whole artifactId") {
    val update = ("org.typelevel".g % "log4cats-core".a % "2.1.1" %> "2.4.0").single
    val obtained = scalafixMigrationsFinder.findMigrations(update)
    val expected = (List(), List())
    assertEquals(obtained, expected)
  }
}
