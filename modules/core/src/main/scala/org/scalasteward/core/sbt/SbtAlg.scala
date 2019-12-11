/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.sbt

import better.files.File
import cats.data.OptionT
import cats.implicits._
import cats.{Functor, Monad}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.io.{FileAlg, FileData, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.sbt.command._
import org.scalasteward.core.sbt.data.SbtVersion
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

trait SbtAlg[F[_]] {
  def addGlobalPlugin(plugin: FileData): F[Unit]

  def addGlobalPluginTemporarily[A](plugin: FileData)(fa: F[A]): F[A]

  def addGlobalPlugins: F[Unit]

  def getSbtVersion(repo: Repo): F[Option[SbtVersion]]

  def getDependencies(repo: Repo): F[List[Dependency]]

  def getUpdatesForRepo(repo: Repo): F[List[Update.Single]]

  def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit]

  final def getSbtDependency(repo: Repo)(implicit F: Functor[F]): F[Option[Dependency]] =
    OptionT(getSbtVersion(repo)).subflatMap(sbtDependency).value
}

object SbtAlg {
  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      logger: Logger[F],
      processAlg: ProcessAlg[F],
      scalafmtAlg: ScalafmtAlg[F],
      updateAlg: UpdateAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): SbtAlg[F] =
    new SbtAlg[F] {
      override def addGlobalPlugin(plugin: FileData): F[Unit] =
        List("0.13", "1.0").traverse_ { series =>
          sbtDir.flatMap(dir => fileAlg.writeFileData(dir / series / "plugins", plugin))
        }

      override def addGlobalPluginTemporarily[A](plugin: FileData)(fa: F[A]): F[A] =
        sbtDir.flatMap { dir =>
          val plugins = "plugins"
          fileAlg.createTemporarily(dir / "0.13" / plugins / plugin.name, plugin.content) {
            fileAlg.createTemporarily(dir / "1.0" / plugins / plugin.name, plugin.content) {
              fa
            }
          }
        }

      override def addGlobalPlugins: F[Unit] =
        for {
          _ <- logger.info("Add global sbt plugins")
          _ <- addGlobalPlugin(scalaStewardSbt)
          _ <- addGlobalPlugin(stewardPlugin)
        } yield ()

      override def getSbtVersion(repo: Repo): F[Option[SbtVersion]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          maybeProperties <- fileAlg.readFile(repoDir / "project" / "build.properties")
          version = maybeProperties.flatMap(parser.parseBuildProperties)
        } yield version

      override def getDependencies(repo: Repo): F[List[Dependency]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          cmd = sbtCmd(List(stewardDependencies, reloadPlugins, stewardDependencies))
          lines <- exec(cmd, repoDir)
          dependencies = parser.parseDependencies(lines)
          maybeSbtDependency <- getSbtDependency(repo)
          maybeScalafmtDependency <- scalafmtAlg.getScalafmtDependency(repo)
        } yield (maybeSbtDependency.toList ++ maybeScalafmtDependency.toList ++ dependencies).distinct

      override def getUpdatesForRepo(repo: Repo): F[List[Update.Single]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          commands = {
            val stewardCommands = List(stewardDependencies, stewardUpdates)
            stewardCommands ++ List(reloadPlugins) ++ stewardCommands
          }
          lines <- exec(sbtCmd(commands), repoDir)
          (dependencies, updates) = parser.parseDependenciesAndUpdates(lines)
          upToDateDependencies = dependencies.diff(updates.map(_.dependency))
          updatesWithNewGroupId = upToDateDependencies.flatMap(UpdateAlg.findUpdateUnderNewGroup)
          additionalUpdates <- findAdditionalUpdates(repo)
          result = (updates.map(_.toUpdate) ++ updatesWithNewGroupId ++ additionalUpdates).distinct
            .sortBy(update => (update.groupId, update.artifactId, update.currentVersion))
        } yield result

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] =
        addGlobalPluginTemporarily(scalaStewardScalafixSbt) {
          for {
            repoDir <- workspaceAlg.repoDir(repo)
            scalafixCmds = for {
              migration <- migrations
              rule <- migration.rewriteRules
              cmd <- Nel.of(scalafix, testScalafix)
            } yield s"$cmd $rule"
            _ <- exec(sbtCmd(scalafixEnable :: scalafixCmds.toList), repoDir)
          } yield ()
        }

      val sbtDir: F[File] =
        fileAlg.home.map(_ / ".sbt")

      def exec(command: Nel[String], repoDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(repoDir)(processAlg.execSandboxed(command, repoDir))

      def sbtCmd(commands: List[String]): Nel[String] =
        Nel.of("sbt", "-batch", "-no-colors", commands.mkString(";", ";", ""))

      def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

      def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        fileAlg.removeTemporarily(dir / ".jvmopts") {
          fileAlg.removeTemporarily(dir / ".sbtopts") {
            fa
          }
        }

      def findAdditionalUpdates(repo: Repo): F[List[Update.Single]] =
        for {
          maybeSbtDependency <- getSbtDependency(repo)
          maybeScalafmtDependency <- scalafmtAlg.getScalafmtDependency(repo)
          maybeSbtUpdate <- maybeSbtDependency.flatTraverse(updateAlg.findUpdate)
          maybeScalafmtUpdate <- maybeScalafmtDependency.flatTraverse(updateAlg.findUpdate)
        } yield maybeSbtUpdate.toList ++ maybeScalafmtUpdate.toList
    }
}
