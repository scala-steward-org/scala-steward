package org.scalasteward.core.scalafix

import munit.FunSuite
import org.http4s.Uri
import org.scalasteward.core.application.Config.ScalafixCfg
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.MockContext.context.migrationsLoader
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.scalafix.MigrationsLoaderTest.mockState
import org.scalasteward.core.util.Nel

class MigrationsLoaderTest extends FunSuite {
  val migrationsUri: Uri = Uri.unsafeFromString("/tmp/scala-steward/extra-migrations.conf")
  val migrationsContent: String =
    """|migrations = [
       |  {
       |    groupId: "org.ice.cream",
       |    artifactIds: ["yumyum-.*"],
       |    newVersion: "1.0.0",
       |    rewriteRules: ["awesome rewrite rule"],
       |    doc: "https://scalacenter.github.io/scalafix/"
       |  }
       |]""".stripMargin
  val migration: Migration = Migration(
    GroupId("org.ice.cream"),
    Nel.of("yumyum-.*"),
    Version("1.0.0"),
    Nel.of("awesome rewrite rule"),
    Some("https://scalacenter.github.io/scalafix/"),
    None
  )

  test("loadAll: without extra file, without defaults") {
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(Nil, disableDefaults = true))
      .runA(mockState)
      .unsafeRunSync()
    assertEquals(migrations.size, 0)
  }

  test("loadAll: without extra file, with defaults") {
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(Nil, disableDefaults = false))
      .runA(mockState)
      .unsafeRunSync()
    assert(clue(migrations.size) > 0)
  }

  test("loadAll: with extra file, without defaults") {
    val initialState = mockState.addUri(migrationsUri, migrationsContent)
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = true))
      .runA(initialState)
      .unsafeRunSync()
    assertEquals(migrations, List(migration))
  }

  test("loadAll: with extra file, with defaults") {
    val initialState = mockState.addUri(migrationsUri, migrationsContent)
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .unsafeRunSync()
    assert(clue(migrations.size) > 1)
    assert(clue(migrations).contains(migration))
  }

  test("loadAll: malformed extra file") {
    val initialState = mockState.addUri(migrationsUri, """{"key": "i'm not a valid Migration}""")
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .attempt
      .unsafeRunSync()
    assert(migrations.isLeft)
  }
}

object MigrationsLoaderTest {
  val mockState: MockState = MockState.empty.addUri(
    MigrationsLoader.defaultScalafixMigrationsUrl,
    ioFileAlg.readResource("scalafix-migrations.conf").unsafeRunSync()
  )
}
