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

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.Uri
import org.scalasteward.core.application.Config.ScalafixCfg
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.edit.scalafix.ScalafixMigration.ExecutionOrder.PreUpdate
import org.scalasteward.core.edit.scalafix.ScalafixMigration.Target.Sources
import org.scalasteward.core.git.Author
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockContext.context.scalafixMigrationsLoader
import org.scalasteward.core.mock.MockContext.mockState
import org.scalasteward.core.mock.MockEffOps
import org.scalasteward.core.util.Nel

class ScalafixMigrationsLoaderTest extends FunSuite {
  val migrationsUri: Uri = Uri.unsafeFromString(s"$mockRoot/extra-migrations.conf")
  val migrationsContent: String =
    """|migrations = [
       |  {
       |    groupId: "org.ice.cream",
       |    artifactIds: ["yumyum-.*"],
       |    newVersion: "1.0.0",
       |    rewriteRules: ["awesome rewrite rule"],
       |    doc: "https://scalacenter.github.io/scalafix/",
       |    authors: ["Jane Doe <jane@example.com>"],
       |    target: "sources",
       |    executionOrder: "pre-update"
       |  }
       |]""".stripMargin
  val migration: ScalafixMigration = ScalafixMigration(
    GroupId("org.ice.cream"),
    Nel.of("yumyum-.*"),
    Version("1.0.0"),
    Nel.of("awesome rewrite rule"),
    Some("https://scalacenter.github.io/scalafix/"),
    authors = Some(Nel.of(Author("Jane Doe", "jane@example.com"))),
    target = Some(Sources),
    executionOrder = Some(PreUpdate)
  )

  test("loadAll: without extra file, without defaults") {
    val migrations = scalafixMigrationsLoader
      .loadAll(ScalafixCfg(Nil, disableDefaults = true))
      .runA(mockState)
      .unsafeRunSync()
    assertEquals(migrations.size, 0)
  }

  test("loadAll: without extra file, with defaults") {
    val migrations = scalafixMigrationsLoader
      .loadAll(ScalafixCfg(Nil, disableDefaults = false))
      .runA(mockState)
      .unsafeRunSync()
    assert(clue(migrations.size) > 0)
  }

  test("loadAll: with extra file, without defaults") {
    val initialState = mockState.addUris(migrationsUri -> migrationsContent)
    val migrations = scalafixMigrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = true))
      .runA(initialState)
      .unsafeRunSync()
    assertEquals(migrations, List(migration))
  }

  test("loadAll: with extra file, with defaults") {
    val initialState = mockState.addUris(migrationsUri -> migrationsContent)
    val migrations = scalafixMigrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .unsafeRunSync()
    assert(clue(migrations.size) > 1)
    assert(clue(migrations).contains(migration))
  }

  test("loadAll: malformed extra file") {
    val initialState = mockState.addUris(migrationsUri -> """{"key": "i'm not a valid Migration}""")
    val migrations = scalafixMigrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .attempt
      .unsafeRunSync()
    assert(migrations.isLeft)
  }
}
