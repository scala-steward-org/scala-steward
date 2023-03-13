/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.core.buildtool.scalacli

import cats.Monad
import cats.syntax.all._
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolAlg}
import org.scalasteward.core.data.Scope
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.process.SlurpOptions
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class ScalaCliAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    sbtAlg: SbtAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) extends BuildToolAlg[F] {
  override def name: String = "Scala CLI"

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] = {
    val buildRootPath = buildRoot.relativePath.dropWhile(Set('.', '/'))
    val extensions = Set(".sc", ".scala")
    gitAlg
      .findFilesContaining(buildRoot.repo, "//> using lib ")
      .map(_.exists(path => path.startsWith(buildRootPath) && extensions.exists(path.endsWith)))
  }

  override def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      exportDir = "tmp-sbt-build-for-scala-steward"
      exportCmd = Nel.of(
        "scala-cli",
        "--power",
        "export",
        "--sbt",
        "--output",
        exportDir,
        buildRootDir.pathAsString
      )
      slurpOptions = SlurpOptions.ignoreBufferOverflow
      _ <- processAlg.execSandboxed(exportCmd, buildRootDir, slurpOptions = slurpOptions)
      exportBuildRoot = buildRoot.copy(relativePath = buildRoot.relativePath + s"/$exportDir")
      dependencies <- sbtAlg.getDependencies(exportBuildRoot)
      _ <- fileAlg.deleteForce(buildRootDir / exportDir)
    } yield dependencies

  override def runMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    logger.warn(
      s"Scalafix migrations are currently not supported in $name projects, see https://github.com/VirtusLab/scala-cli/issues/647 for details"
    )
}
