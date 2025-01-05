package org.scalasteward.core.update.artifact

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.Uri
import org.scalasteward.core.application.Config.ArtifactCfg
import org.scalasteward.core.data.GroupId
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockContext.context.artifactMigrationsLoader
import org.scalasteward.core.mock.MockContext.mockState
import org.scalasteward.core.mock.MockEffOps

class ArtifactMigrationsLoaderTest extends FunSuite {
  val migrationsUri: Uri = Uri.unsafeFromString(s"$mockRoot/extra-migrations.conf")
  val migrationsContent: String =
    """|changes = [
       |  {
       |    groupIdBefore = com.evilcorp
       |    groupIdAfter = org.ice.cream
       |    artifactIdAfter = yumyum
       |  }
       |]""".stripMargin
  val migration: ArtifactChange = ArtifactChange(
    groupIdBefore = Some(GroupId("com.evilcorp")),
    groupIdAfter = GroupId("org.ice.cream"),
    artifactIdBefore = None,
    artifactIdAfter = "yumyum"
  )

  test("loadAll: without extra file, without defaults") {
    val migrations = artifactMigrationsLoader
      .loadAll(ArtifactCfg(Nil, disableDefaults = true))
      .runA(mockState)
      .unsafeRunSync()
    assertEquals(migrations.size, 0)
  }

  test("loadAll: without extra file, with defaults") {
    val migrations = artifactMigrationsLoader
      .loadAll(ArtifactCfg(Nil, disableDefaults = false))
      .runA(mockState)
      .unsafeRunSync()
    assert(clue(migrations.size) > 0)
  }

  test("loadAll: with extra file, without defaults") {
    val initialState = mockState.addUris(migrationsUri -> migrationsContent)
    val migrations = artifactMigrationsLoader
      .loadAll(ArtifactCfg(List(migrationsUri), disableDefaults = true))
      .runA(initialState)
      .unsafeRunSync()
    assertEquals(migrations, List(migration))
  }

  test("loadAll: with extra file, with defaults") {
    val initialState = mockState.addUris(migrationsUri -> migrationsContent)
    val migrations = artifactMigrationsLoader
      .loadAll(ArtifactCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .unsafeRunSync()
    assert(clue(migrations.size) > 1)
    assert(clue(migrations).contains(migration))
  }

  test("loadAll: malformed extra file") {
    val initialState = mockState.addUris(migrationsUri -> """{"key": "i'm not a valid Migration}""")
    val migrations = artifactMigrationsLoader
      .loadAll(ArtifactCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .attempt
      .unsafeRunSync()
    assert(migrations.isLeft)
  }

  test(
    "loadAll: issue #2238 decoding should fail if both groupIdBefore and artifactIdBefore are missing"
  ) {
    val initialState = mockState.addUris(migrationsUri -> """|changes = [
                                                             |  {
                                                             |    groupIdAfter = org.ice.cream
                                                             |    artifactIdAfter = yumyum
                                                             |  }
                                                             |]""".stripMargin)
    val migrations = artifactMigrationsLoader
      .loadAll(ArtifactCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .attempt
      .unsafeRunSync()
    assert(migrations.isLeft)
  }

  test("loadAll: check for duplicated entries in 'artifact-migrations.conf'") {
    def versionLessArtifactChange(ac: ArtifactChange) =
      (ac.groupIdBefore, ac.groupIdAfter, ac.artifactIdBefore, ac.artifactIdAfter)
    val migrations = artifactMigrationsLoader
      .loadAll(ArtifactCfg(Nil, disableDefaults = false))
      .runA(mockState)
      .unsafeRunSync()
    val duplicates = migrations
      .diff(migrations.distinctBy(versionLessArtifactChange))
      .distinctBy(versionLessArtifactChange)
    assert(clue(duplicates).isEmpty)
  }
}
