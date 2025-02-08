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

package org.scalasteward.core.buildtool.scalacli

import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolAlg}
import org.scalasteward.core.data.Scope
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.process.SlurpOptions
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

object ScalaCliAlg {
  val directives =
    // sourced from https://github.com/VirtusLab/scala-cli/blob/9e22d4a91ba8699ac2727d2ac3042d64abe951e1/modules/directives/src/main/scala/scala/build/preprocessing/directives/Dependency.scala#L33-L48
    List(
      "lib",
      "libs",
      "dep",
      "deps",
      "dependencies",
      "toolkit",
      "test.dependency",
      "test.dep",
      "test.deps",
      "test.dependencies",
      "compileOnly.lib",
      "compileOnly.libs",
      "compileOnly.dep",
      "compileOnly.deps",
      "compileOnly.dependencies"
    ).map(alias => s"//> using $alias ")
}

final class ScalaCliAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    override protected val logger: Logger[F],
    processAlg: ProcessAlg[F],
    sbtAlg: SbtAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) extends BuildToolAlg[F] {
  override def name: String = "Scala CLI"

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] = {
    val buildRootPath = buildRoot.relativePath.dropWhile(Set('.', '/'))
    val extensions = Set(".sc", ".scala")
    ScalaCliAlg.directives
      .flatTraverse(gitAlg.findFilesContaining(buildRoot.repo, _))
      .map(_.exists(path => path.startsWith(buildRootPath) && extensions.exists(path.endsWith)))
  }

  override def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    getDependenciesViaSbtExport(buildRoot)

  private def getDependenciesViaSbtExport(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
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
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      cmd = Nel.of(
        "scala-cli",
        "--power",
        "fix",
        "--enable-built-in-rules=false",
        "--scalafix-rules"
      ) ::: migration.rewriteRules.append(buildRootDir.pathAsString)
      slurpOptions = SlurpOptions.ignoreBufferOverflow
      _ <- processAlg.execSandboxed(cmd, buildRootDir, slurpOptions = slurpOptions)
    } yield ()
}
