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

package org.scalasteward.core.buildtool.sbt

import better.files.File
import cats.data.{NonEmptyList, OptionT}
import cats.effect.{Concurrent, Resource}
import cats.syntax.all._
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolAlg}
import org.scalasteward.core.coursier.VersionsCache
import org.scalasteward.core.data.{Dependency, Scope, Version}
import org.scalasteward.core.edit.scalafix.{ScalafixCli, ScalafixMigration}
import org.scalasteward.core.io.process.SlurpOptions
import org.scalasteward.core.io.{FileAlg, FileData, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.buildtool.sbt.scalaStewardSbtScalafix

final class SbtAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    processAlg: ProcessAlg[F],
    scalafixCli: ScalafixCli[F],
    workspaceAlg: WorkspaceAlg[F],
    versionsCache: VersionsCache[F],
    F: Concurrent[F]
) extends BuildToolAlg[F] {
  override def name: String = "sbt"

  override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
    workspaceAlg
      .buildRootDir(buildRoot)
      .flatMap(buildRootDir => fileAlg.isRegularFile(buildRootDir / "build.sbt"))

  private def getSbtVersion(buildRootDir: File): F[Option[Version]] =
    for {
      maybeProperties <- fileAlg.readFile(buildRootDir / project / buildPropertiesName)
      version = maybeProperties.flatMap(parser.parseBuildProperties)
    } yield version

  private def metaBuildsCount(buildRootDir: File): F[Int] =
    fs2.Stream
      .iterate(buildRootDir / project)(_ / project)
      .take(5L) // Use an upper bound for the meta-builds count to prevent DoS attacks.
      .evalMap(fileAlg.isDirectory)
      .takeWhile(identity)
      .compile
      .count
      .map(_.toInt)

  override def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      maybeSbtVersion <- getSbtVersion(buildRootDir)
      metaBuilds <- metaBuildsCount(buildRootDir)
      lines <- addStewardPluginTemporarily(buildRootDir, maybeSbtVersion, metaBuilds).surround {
        val commands = Nel.of(crossStewardDependencies) ++
          List.fill(metaBuilds)(List(reloadPlugins, stewardDependencies)).flatten
        sbt(commands, buildRootDir)
      }
      dependencies = parser.parseDependencies(lines)
      maybeSbtDependency = maybeSbtVersion.flatMap(scopedSbtDependency).map(_.map(List(_))).toList
    } yield maybeSbtDependency ::: dependencies

  private def addStewardPluginTemporarily(
      buildRootDir: File,
      maybeSbtVersion: Option[Version],
      metaBuilds: Int
  ): Resource[F, Unit] =
    for {
      _ <- Resource.unit[F]
      pluginVersion = maybeSbtVersion match {
        case Some(v) if v < Version("1.3.11") => "1_0_0"
        case _                                => "1_3_11"
      }
      plugin <- Resource.eval(stewardPlugin(pluginVersion))
      _ <- List
        .iterate(buildRootDir / project, metaBuilds + 1)(_ / project)
        .collectFold(fileAlg.createTemporarily(_, plugin))
    } yield ()

  private def stewardPlugin(version: String): F[FileData] = {
    val name = s"StewardPlugin_$version.scala"
    fileAlg.readResource(name).map(FileData(s"scala-steward-$name", _))
  }

  private def scopedSbtDependency(sbtVersion: Version): Option[Scope[Dependency]] =
    sbtDependency(sbtVersion).map(dep => Scope(dep, List(config.defaultResolver)))

  override def runMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    migration.targetOrDefault match {
      case ScalafixMigration.Target.Sources => runSourcesMigration(buildRoot, migration)
      case ScalafixMigration.Target.Build   => runBuildMigration(buildRoot, migration)
    }

  private def runSourcesMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      _ <- runSbtScalafix(buildRootDir, migration, metaBuilds = 0, startDepth = 0)
    } yield ()

  private def runBuildMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      metaBuilds <- metaBuildsCount(buildRootDir)
      _ <- runSyntacticBuildMigrations(buildRootDir, migration)
      _ <- runSbtScalafix(buildRootDir, migration, metaBuilds, startDepth = 1)
    } yield ()

  private def runSyntacticBuildMigrations(
      buildRootDir: File,
      migration: ScalafixMigration
  ): F[Unit] = {
    val rootSbtFiles =
      fileAlg.walk(buildRootDir, 1).filter(_.extension.contains(".sbt"))

    val metaBuildFiles =
      fileAlg.walk(buildRootDir / project, 3).filter(_.extension.exists(Set(".sbt", ".scala")))

    val allBuildFiles = (rootSbtFiles ++ metaBuildFiles).compile.toList

    allBuildFiles.flatMap { buildFiles =>
      Nel.fromList(buildFiles).fold(F.unit) { files =>
        scalafixCli.runMigration(buildRootDir, files, migration)
      }
    }
  }

  private def latestSbtScalafixVersion: F[Option[Version]] =
    versionsCache
      .getVersions(Scope(sbtScalafixDependency, List(config.defaultResolver)), None)
      .map(_.lastOption)

  private def addScalafixPluginTemporarily(
      buildRootDir: File,
      pluginVersion: Version,
      metaBuilds: Int,
      startDepth: Int
  ): Resource[F, Unit] = {
    val buildsDepth = metaBuilds + startDepth
    val plugin = scalaStewardSbtScalafix(pluginVersion)
    List
      .iterate(buildRootDir / project, buildsDepth + 1)(_ / project)
      .drop(startDepth)
      .collectFold(fileAlg.createTemporarily(_, plugin))
  }

  private def addScalacOptionsTemporarily(
      buildRootDir: File,
      scalacOptions: Option[Nel[String]],
      metaBuilds: Int,
      startDepth: Int
  ): Resource[F, Unit] =
    scalacOptions.fold(Resource.unit[F]) { opts =>
      val buildsDepth = metaBuilds + startDepth
      val options = scalaStewardScalafixOptions(opts.toList)
      List
        .iterate(buildRootDir, buildsDepth + 1)(_ / project)
        .drop(startDepth)
        .collectFold(fileAlg.createTemporarily(_, options))
    }

  private def runSbtScalafix(
      buildRootDir: File,
      migration: ScalafixMigration,
      metaBuilds: Int,
      startDepth: Int
  ): F[Unit] =
    OptionT(latestSbtScalafixVersion).foreachF { pluginVersion =>
      addScalafixPluginTemporarily(buildRootDir, pluginVersion, metaBuilds, startDepth)
        .surround {
          addScalacOptionsTemporarily(buildRootDir, migration.scalacOptions, metaBuilds, startDepth)
            .surround {
              val scalafixCmds = migration.rewriteRules.map(rule => s"$scalafixAll $rule").toList
              val slurpOptions = SlurpOptions.ignoreBufferOverflow
              val buildsDepth = metaBuilds + startDepth
              val scalafixCommands = scalafixEnable :: scalafixCmds
              val commandLists =
                (scalafixCommands :: List.fill(buildsDepth)(reloadPlugins :: scalafixCommands))
                  .drop(startDepth)
              val commands = NonEmptyList.fromList(commandLists.flatten)
              commands.fold(F.unit) { cmds =>
                sbt(
                  cmds,
                  buildRootDir,
                  slurpOptions
                ).void
              }
            }
        }
    }

  private def sbt(
      sbtCommands: Nel[String],
      repoDir: File,
      slurpOptions: SlurpOptions = Set.empty
  ): F[List[String]] =
    maybeIgnoreOptsFiles(repoDir).surround {
      val command =
        Nel.of(
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          "-Dsbt.server.forcestart=true",
          sbtCommands.mkString_(";", ";", "")
        )
      processAlg.execSandboxed(command, repoDir, slurpOptions = slurpOptions)
    }

  private def maybeIgnoreOptsFiles(dir: File): Resource[F, Unit] =
    if (config.ignoreOptsFiles) ignoreOptsFiles(dir) else Resource.unit[F]

  private def ignoreOptsFiles(dir: File): Resource[F, Unit] =
    List(".jvmopts", ".sbtopts").traverse_(file => fileAlg.removeTemporarily(dir / file))

  private val project = "project"
}
