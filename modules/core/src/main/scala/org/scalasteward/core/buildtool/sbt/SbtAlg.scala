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

package org.scalasteward.core.buildtool.sbt

import better.files.File
import cats.data.OptionT
import cats.effect.{Concurrent, Resource}
import cats.syntax.all._
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildToolAlg
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.coursier.VersionsCache
import org.scalasteward.core.data.{Dependency, Scope}
import org.scalasteward.core.edit.scalafix.{ScalafixCli, ScalafixMigration}
import org.scalasteward.core.io.{FileAlg, FileData, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.BuildRoot
import org.typelevel.log4cats.Logger

final class SbtAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    scalafixCli: ScalafixCli[F],
    workspaceAlg: WorkspaceAlg[F],
    versionsCache: VersionsCache[F],
    F: Concurrent[F]
) extends BuildToolAlg[F] {
  private def getSbtDependency(buildRoot: BuildRoot): F[Option[Dependency]] =
    OptionT(getSbtVersion(buildRoot)).subflatMap(sbtDependency).value

  private def addGlobalPluginTemporarily(plugin: FileData): Resource[F, Unit] =
    Resource.eval(sbtDir).flatMap { dir =>
      List("0.13", "1.0").traverse_ { version =>
        fileAlg.createTemporarily(dir / version / "plugins" / plugin.name, plugin.content)
      }
    }

  def addGlobalPlugins: Resource[F, Unit] =
    Resource.eval(logger.info("Add global sbt plugins")) >>
      Resource.eval(stewardPlugin).flatMap(addGlobalPluginTemporarily)

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
    workspaceAlg
      .buildRootDir(buildRoot)
      .flatMap(buildRootDir => fileAlg.isRegularFile(buildRootDir / "build.sbt"))

  private def getSbtVersion(buildRoot: BuildRoot): F[Option[SbtVersion]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      maybeProperties <- fileAlg.readFile(buildRootDir / "project" / "build.properties")
      version = maybeProperties.flatMap(parser.parseBuildProperties)
    } yield version

  override def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      commands = Nel.of(crossStewardDependencies, reloadPlugins, stewardDependencies)
      lines <- sbt(commands, buildRootDir)
      dependencies = parser.parseDependencies(lines)
      additionalDependencies <- getAdditionalDependencies(buildRoot)
    } yield additionalDependencies ::: dependencies

  override def runMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    migration.targetOrDefault match {
      case ScalafixMigration.Target.Sources => runSourcesMigration(buildRoot, migration)
      case ScalafixMigration.Target.Build   => runBuildMigration(buildRoot, migration)
    }

  private def runSourcesMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    sbtScalaFixPluginVersion.foreachF { pluginVersion =>
      addGlobalPluginTemporarily(scalaStewardScalafixSbt(pluginVersion)).surround {
        workspaceAlg.buildRootDir(buildRoot).flatMap { buildRootDir =>
          val withScalacOptions =
            migration.scalacOptions.fold(Resource.unit[F]) { opts =>
              val file = scalaStewardScalafixOptions(opts.toList)
              fileAlg.createTemporarily(buildRootDir / file.name, file.content)
            }
          val scalafixCmds = migration.rewriteRules.map(rule => s"$scalafixAll $rule").toList
          withScalacOptions.surround(sbt(Nel(scalafixEnable, scalafixCmds), buildRootDir).void)
        }
      }
    }

  private def sbtScalaFixPluginVersion: OptionT[F, String] =
    OptionT(
      versionsCache
        .getVersions(Scope(sbtScalaFixDependency, List(config.defaultResolver)), None)
        .map(_.lastOption.map(_.value))
    )

  private def runBuildMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      projectDir = buildRootDir / "project"
      files0 <- (
        fileAlg.walk(buildRootDir, 1).filter(_.extension.contains(".sbt")) ++
          fileAlg.walk(projectDir, 1).filter(_.extension.exists(Set(".sbt", ".scala")))
      ).compile.toList
      _ <- Nel.fromList(files0).fold(F.unit) { files1 =>
        scalafixCli.runMigration(buildRootDir, files1, migration)
      }
    } yield ()

  private val sbtDir: F[File] =
    fileAlg.home.map(_ / ".sbt")

  private def sbt(sbtCommands: Nel[String], repoDir: File): F[List[String]] =
    maybeIgnoreOptsFiles(repoDir).surround {
      val command =
        Nel.of(
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          sbtCommands.mkString_(";", ";", "")
        )
      processAlg.execSandboxed(command, repoDir)
    }

  private def maybeIgnoreOptsFiles[A](dir: File): Resource[F, Unit] =
    if (config.ignoreOptsFiles) ignoreOptsFiles(dir) else Resource.unit[F]

  private def ignoreOptsFiles(dir: File): Resource[F, Unit] =
    List(".jvmopts", ".sbtopts").traverse_(file => fileAlg.removeTemporarily(dir / file))

  private def getAdditionalDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    getSbtDependency(buildRoot)
      .map(_.map(dep => Scope(List(dep), List(config.defaultResolver))).toList)
}
