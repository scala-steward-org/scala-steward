package org.scalasteward.core.scalafix

import better.files.File
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MigrationAlgTest extends AnyFunSuite with Matchers {
  val extraFile: File = config.workspace / "extra-migrations.conf"

  test("loadMigrations with extra file") {
    val content =
      """|migrations = [
         |  {
         |    groupId: "org.ice.cream",
         |    artifactIds: ["yumyum-.*"],
         |    newVersion: "1.0.0",
         |    rewriteRules: ["awesome rewrite rule"],
         |    doc: "https://scalacenter.github.io/scalafix/"
         |  }
         |]""".stripMargin
    val initialState = MockState.empty.add(extraFile, content)
    val migrations =
      MigrationAlg.loadMigrations[MockEff](Some(extraFile)).runA(initialState).unsafeRunSync

    migrations.size should be > 1
    (migrations should contain).oneElementOf(
      List(
        Migration(
          GroupId("org.ice.cream"),
          Nel.of("yumyum-.*"),
          Version("1.0.0"),
          Nel.of("awesome rewrite rule"),
          Some("https://scalacenter.github.io/scalafix/")
        )
      )
    )
  }

  test("loadMigrations with extra file and disableDefaults = true") {
    val content =
      """|disableDefaults = true
         |migrations = [
         |  {
         |    groupId: "org.ice.cream",
         |    artifactIds: ["yumyum-.*"],
         |    newVersion: "1.0.0",
         |    rewriteRules: ["awesome rewrite rule"]
         |  }
         |]""".stripMargin
    val initialState = MockState.empty.add(extraFile, content)
    val migrations =
      MigrationAlg.loadMigrations[MockEff](Some(extraFile)).runA(initialState).unsafeRunSync

    migrations shouldBe List(
      Migration(
        GroupId("org.ice.cream"),
        Nel.of("yumyum-.*"),
        Version("1.0.0"),
        Nel.of("awesome rewrite rule"),
        None
      )
    )
  }

  test("loadMigrations with extra file and disableDefaults = true only") {
    val initialState = MockState.empty.add(extraFile, "disableDefaults = true")
    val migrations =
      MigrationAlg.loadMigrations[MockEff](Some(extraFile)).runA(initialState).unsafeRunSync
    migrations.isEmpty shouldBe true
  }

  test("loadMigrations with malformed extra file") {
    val initialState = MockState.empty.add(extraFile, """{"key": "i'm not a valid Migration}""")
    val migrations =
      MigrationAlg.loadMigrations[MockEff](Some(extraFile)).runA(initialState).attempt.unsafeRunSync
    migrations.isLeft shouldBe true
  }

  test("loadMigrations without extra file") {
    val migrations =
      MigrationAlg.loadMigrations[MockEff](None).runA(MockState.empty).unsafeRunSync()
    migrations.size shouldBe 11
  }
}
