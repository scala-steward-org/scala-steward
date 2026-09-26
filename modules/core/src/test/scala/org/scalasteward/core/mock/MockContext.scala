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

package org.scalasteward.core.mock

import cats.data.Kleisli
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import org.http4s.client.Client
import org.scalasteward.core.application.{Config, Context, ValidateRepoConfigContext}
import org.scalasteward.core.edit.scalafix.ScalafixMigrationsLoader
import org.scalasteward.core.io.*
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.repoconfig.RepoConfigLoader
import org.scalasteward.core.update.artifact.ArtifactMigrationsLoader
import org.scalasteward.core.util.UrlCheckerClient
import org.typelevel.log4cats.Logger

object MockContext {
  implicit private val client: Client[MockEff] =
    Client[MockEff] { request =>
      Resource.eval {
        Kleisli { mockCtx =>
          mockCtx.get.flatMap { mockState =>
            mockState.clientResponses.run(request).run(mockCtx)
          }
        }
      }
    }

  implicit private val urlCheckerClient: UrlCheckerClient[MockEff] = UrlCheckerClient(client)
  implicit private val fileAlg: FileAlg[MockEff] = new MockFileAlg
  implicit private val logger: Logger[MockEff] = new MockLogger
  implicit private val processAlg: ProcessAlg[MockEff] = MockProcessAlg.create(config.processCfg)
  implicit private val workspaceAlg: WorkspaceAlg[MockEff] = new MockWorkspaceAlg

  val mockState: MockState = MockState.empty.addUris(
    RepoConfigLoader.defaultRepoConfigUrl ->
      ioFileAlg.readResource("default.scala-steward.conf").unsafeRunSync(),
    ArtifactMigrationsLoader.defaultArtifactMigrationsUrl ->
      ioFileAlg.readResource("artifact-migrations.v2.conf").unsafeRunSync(),
    ScalafixMigrationsLoader.defaultScalafixMigrationsUrl ->
      ioFileAlg.readResource("scalafix-migrations.conf").unsafeRunSync()
  )

  val context: Context[MockEff] = context(config)
  def context(stewardConfig: Config): Context[MockEff] =
    mockState.toRef.flatMap(Context.step1(stewardConfig).run).unsafeRunSync()

  val validateRepoConfigContext: ValidateRepoConfigContext[MockEff] =
    ValidateRepoConfigContext.step1
}
