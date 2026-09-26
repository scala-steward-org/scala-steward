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

package org.scalasteward.core.repoconfig

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.Uri
import org.scalasteward.core.application.Config.RepoConfigCfg
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockContext.context.{fileAlg, logger}
import org.scalasteward.core.mock.MockContext.mockState
import org.scalasteward.core.mock.MockEffOps

class RepoConfigLoaderTest extends FunSuite {
  test("config file merging order") {
    val config1 =
      """
        |updates.pin = [
        |  { groupId = "org.scala-lang", artifactId="scala3-compiler", version = "3.3." },
        |  { groupId = "org.scala-lang", artifactId="scala3-compiler", version = "3.5." },
        |]
        |""".stripMargin
    val config2 =
      """
        |updates.pin = [
        |  { groupId = "org.scala-lang", artifactId="scala3-compiler", version = "3.4." },
        |]
        |""".stripMargin

    val uri1 = Uri.unsafeFromString(s"$mockRoot/test1.scala-steward.conf")
    val uri2 = Uri.unsafeFromString(s"$mockRoot/test2.scala-steward.conf")

    val initialState = mockState.addUris(uri1 -> config1, uri2 -> config2)

    val repoConfigLoader = new RepoConfigLoader
    val repoConfig = repoConfigLoader
      .loadGlobalRepoConfig(RepoConfigCfg(repoConfigs = List(uri1, uri2), disableDefault = true))
      .runA(initialState)
      .attempt
      .unsafeRunSync()
      .getOrElse(None)
    assert(clue(repoConfig).isDefined)
    assertEquals(
      repoConfig.get.updatesOrDefault.pinOrDefault.head.version,
      Some(VersionPattern(Some("3.4.")))
    )
  }
}
