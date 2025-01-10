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

import better.files.File
import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.edit.scalafix.ScalafixCli.scalafixBinary
import org.scalasteward.core.io.process.SlurpOptions
import org.scalasteward.core.io.{ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel

final class ScalafixCli[F[_]](implicit
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  def runMigration(workingDir: File, files: Nel[File], migration: ScalafixMigration): F[Unit] = {
    val rules = migration.rewriteRules.map("--rules=" + _)
    val cmd = scalafixBinary :: rules ::: files.map(_.pathAsString)
    processAlg.exec(cmd, workingDir, slurpOptions = SlurpOptions.ignoreBufferOverflow).void
  }

  def version: F[String] = {
    val cmd = Nel.of(scalafixBinary, "--version")
    workspaceAlg.rootDir.flatMap(processAlg.exec(cmd, _)).map(_.mkString.trim)
  }
}

object ScalafixCli {
  val scalafixBinary = "scalafix"
}
