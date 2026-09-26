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

package org.scalasteward.core.io

import better.files.File
import cats.data.Kleisli
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.WorkspaceAlg.RunSummaryFileName
import org.scalasteward.core.mock.{MockConfig, MockEff}

class MockWorkspaceAlg extends WorkspaceAlg[MockEff] {
  override def removeAnyRunSpecificFiles: MockEff[Unit] =
    Kleisli.pure(())

  override def rootDir: MockEff[File] =
    Kleisli.pure(MockConfig.config.workspace)

  override def repoDir(repo: Repo): MockEff[File] =
    rootDir.map(_ / repo.owner / repo.repo)

  override def buildRootDir(buildRoot: BuildRoot): MockEff[File] =
    repoDir(buildRoot.repo).map(_ / buildRoot.relativePath)

  def runSummaryFile: MockEff[File] = rootDir.map(_ / RunSummaryFileName)
}
