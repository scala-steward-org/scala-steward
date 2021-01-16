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
import cats.Functor
import cats.data.OptionT
import cats.effect.BracketThrow
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildToolAlg
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data.{Dependency, Resolver, Scope}
import org.scalasteward.core.io.{FileAlg, FileData, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.BuildRoot

trait SbtAlg[F[_]] extends BuildToolAlg[F, BuildRoot] {
  def addGlobalPluginTemporarily[A](plugin: FileData)(fa: F[A]): F[A]

  def addGlobalPlugins[A](fa: F[A]): F[A]

  def getSbtVersion(buildRoot: BuildRoot): F[Option[SbtVersion]]

  final def getSbtDependency(buildRoot: BuildRoot)(implicit F: Functor[F]): F[Option[Dependency]] =
    OptionT(getSbtVersion(buildRoot)).subflatMap(sbtDependency).value
}

object SbtAlg {
  def create[F[_]](config: Config)(implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: BracketThrow[F]
  ): SbtAlg[F] =
    new SbtAlg[F] {
      override def addGlobalPluginTemporarily[A](plugin: FileData)(fa: F[A]): F[A] =
        sbtDir.flatMap { dir =>
          val plugins = "plugins"
          fileAlg.createTemporarily(dir / "0.13" / plugins / plugin.name, plugin.content) {
            fileAlg.createTemporarily(dir / "1.0" / plugins / plugin.name, plugin.content) {
              fa
            }
          }
        }

      override def addGlobalPlugins[A](fa: F[A]): F[A] =
        logger.info("Add global sbt plugins") >>
          stewardPlugin.flatMap(addGlobalPluginTemporarily(_)(fa))

      override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
        workspaceAlg
          .buildRootDir(buildRoot)
          .flatMap(buildRootDir => fileAlg.isRegularFile(buildRootDir / "build.sbt"))

      override def getSbtVersion(buildRoot: BuildRoot): F[Option[SbtVersion]] =
        for {
          buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
          maybeProperties <- fileAlg.readFile(
            buildRootDir / "project" / "build.properties"
          )
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

      override def runMigration(buildRoot: BuildRoot, migration: Migration): F[Unit] =
        addGlobalPluginTemporarily(scalaStewardScalafixSbt) {
          workspaceAlg.buildRootDir(buildRoot).flatMap { buildRootDir =>
            val withScalacOptions =
              migration.scalacOptions.fold[F[Unit] => F[Unit]](identity) { opts =>
                val file = scalaStewardScalafixOptions(opts.toList)
                fileAlg.createTemporarily(buildRootDir / file.name, file.content)(_)
              }
            val scalafixCmds = migration.rewriteRules.map(rule => s"$scalafixAll $rule").toList
            withScalacOptions(sbt(Nel(scalafixEnable, scalafixCmds), buildRootDir).void)
          }
        }

      val sbtDir: F[File] =
        fileAlg.home.map(_ / ".sbt")

      def sbt(sbtCommands: Nel[String], repoDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(repoDir) {
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

      def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

      def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        fileAlg.removeTemporarily(dir / ".jvmopts") {
          fileAlg.removeTemporarily(dir / ".sbtopts") {
            fa
          }
        }

      def getAdditionalDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
        getSbtDependency(buildRoot)
          .map(_.map(dep => Scope(List(dep), List(Resolver.mavenCentral))).toList)
    }
}
