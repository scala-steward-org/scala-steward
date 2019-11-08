package org.scalasteward.core.scalafix

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.scalafix.{migrations => defaultMigrations}

class MigrationAlgTest extends AnyFunSuite with Matchers {
  val repo = Repo("fthomas", "scala-steward")
  val repoDir = config.workspace / repo.owner / repo.repo
  val scalafmtConf = repoDir / ".scalafix-migrations.conf"
  val configWithMigrations = config.copy(scalafixMigrations = Some(scalafmtConf))
  val alg = MigrationAlg.create(fileAlg, mockLogger, mockEffBracketThrowable, configWithMigrations)

  test("loadMigrations on correct file") {
    val migrationsFile =
      """
        |{
        | "disableDefaults": false,
        | "extraMigrations": [{
        |    "groupId": "org.ice.cream",
        |    "artifactIds": ["yumyum-.*"],
        |    "newVersion": "1.0.0",
        |    "rewriteRules": ["awesome rewrite rule"]}
        |]}""".stripMargin
    val initialState = MockState.empty.add(scalafmtConf, migrationsFile)
    val (_, migrations) =
      alg.loadMigrations.run(initialState).unsafeRunSync

    migrations should contain theSameElementsAs List(
      Migration(
        GroupId("org.ice.cream"),
        Nel.of("yumyum-.*"),
        Version("1.0.0"),
        Nel.of("awesome rewrite rule")
      )
    ) ++ defaultMigrations
  }

  test("loadMigrations with disable defaultMigrations") {
    val migrationsFile =
      """
        |{
        | "disableDefaults": true,
        | "extraMigrations": [{
        |    "groupId": "org.ice.cream",
        |    "artifactIds": ["yumyum-.*"],
        |    "newVersion": "1.0.0",
        |    "rewriteRules": ["awesome rewrite rule"]}
        |]}""".stripMargin
    val initialState = MockState.empty.add(scalafmtConf, migrationsFile)
    val (_, migrations) =
      alg.loadMigrations.run(initialState).unsafeRunSync

    migrations should contain theSameElementsAs List(
      Migration(
        GroupId("org.ice.cream"),
        Nel.of("yumyum-.*"),
        Version("1.0.0"),
        Nel.of("awesome rewrite rule")
      )
    )
  }

  test("loadMigrations on malformed File") {
    val initialState = MockState.empty.add(scalafmtConf, """{"key": "i'm not a valid Migration"}""")
    val (state, migrations) =
      alg.loadMigrations.run(initialState).unsafeRunSync
    migrations shouldBe defaultMigrations
    state.logs shouldBe Vector(None -> "Failed to parse migrations file")
  }

  test("loadMigrations on no File") {
    val (_, migrations) = migrationAlg.loadMigrations.run(MockState.empty).unsafeRunSync

    migrations shouldBe defaultMigrations
  }
}
