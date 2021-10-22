/*
 * Copyright 2018-2021 Scala Steward contributors
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

import cats.effect.Concurrent
import cats.syntax.all._
import org.scalasteward.core.edit.scalafix.ScalafixCli.scalafixBinary
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.BuildRoot

final class ScalafixCli[F[_]](implicit
    fileAlg: FileAlg[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Concurrent[F]
) {
  def runMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      projectDir = buildRootDir / "project"
      files <- (
        fileAlg.walk(buildRootDir, 1).filter(_.extension.contains(".sbt")) ++
          fileAlg.walk(projectDir, 1).filter(_.extension.contains(".scala"))
      ).map(_.pathAsString).compile.toList
      rules = migration.rewriteRules.map("--rules=" + _)
      _ <- processAlg.exec(scalafixBinary :: rules ++ files, buildRootDir)
    } yield ()

  def version: F[String] =
    workspaceAlg.rootDir
      .flatMap(processAlg.exec(Nel.of(scalafixBinary, "--version"), _))
      .map(_.mkString.trim)
}

object ScalafixCli {
  val scalafixBinary = "scalafix"
}
