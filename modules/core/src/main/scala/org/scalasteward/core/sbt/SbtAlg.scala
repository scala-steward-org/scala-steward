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
import cats.Monad
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.io.{FileAlg, FileData, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.repocache.RepoCacheRepository
import org.scalasteward.core.sbt.command._
import org.scalasteward.core.sbt.data.{ArtificialProject, SbtVersion}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.scalafmt.{scalafmtDependency, ScalafmtAlg}
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

trait SbtAlg[F[_]] {
  def addGlobalPlugin(plugin: FileData): F[Unit]

  def addGlobalPluginTemporarily[A](plugin: FileData)(fa: F[A]): F[A]

  def addGlobalPlugins: F[Unit]

  def getSbtVersion(projectDir: File): F[Option[SbtVersion]]

  def getDependencies(projectDirs: List[File]): F[List[Dependency]]

  def getUpdatesForProject(project: ArtificialProject): F[List[Update.Single]]

  def getUpdatesForProjectDirInRepo(projectDir: File, repo: Repo): F[List[Update.Single]]

  def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit]
}

object SbtAlg {
  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      logger: Logger[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      scalafmtAlg: ScalafmtAlg[F],
      cacheRepository: RepoCacheRepository[F],
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

      override def getSbtVersion(projectDir: File): F[Option[SbtVersion]] =
        for {
          maybeProperties <- fileAlg.readFile(projectDir / "project" / "build.properties")
          version = maybeProperties.flatMap(parser.parseBuildProperties)
        } yield version

      override def getDependencies(projectDirs: List[File]): F[List[Dependency]] =
        for {
          originalDependencies <- projectDirs.traverse(getOriginalDependencies).map(_.flatten)
          maybeSbtVersions <- projectDirs.traverse(getSbtVersion)
          maybeSbtDependencies = maybeSbtVersions.flatMap(_.flatMap(sbtDependency))
          maybeScalafmtVersions <- projectDirs.traverse(scalafmtAlg.getScalafmtVersion)
          maybeScalafmtDependencies = maybeScalafmtVersions.flatMap(
            _.map(scalafmtDependency(defaultScalaBinaryVersion))
          )
        } yield (maybeSbtDependencies ++ maybeScalafmtDependencies ++
          originalDependencies).distinct

      def getOriginalDependencies(projectDir: File): F[List[Dependency]] = {
        val cmd = sbtCmd(List(libraryDependenciesAsJson, reloadPlugins, libraryDependenciesAsJson))
        exec(cmd, projectDir).map(parser.parseDependencies)
      }

      override def getUpdatesForProject(project: ArtificialProject): F[List[Update.Single]] =
        for {
          updatesDir <- workspaceAlg.rootDir.map(_ / "updates")
          projectDir = updatesDir / "project"
          _ <- fileAlg.writeFileData(updatesDir, project.mkBuildSbt)
          _ <- fileAlg.writeFileData(projectDir, project.mkBuildProperties)
          _ <- fileAlg.writeFileData(projectDir, project.mkPluginsSbt)
          cmd = sbtCmd(project.dependencyUpdatesCmd)
          lines <- processAlg.exec(cmd, updatesDir)
          _ <- fileAlg.deleteForce(updatesDir)
          updatesWithCrossSuffix = parser.parseSingleUpdates(lines)
          allDeps = project.libraries ++ project.plugins
          uncross = allDeps.map(dep => dep.artifactIdCross -> dep.artifactId).toMap
          updates = updatesWithCrossSuffix.flatMap { update =>
            Update.Single.artifactIdLens.modifyF(uncross.get)(update).toList
          }
        } yield updates

      override def getUpdatesForProjectDirInRepo(
          projectDir: File,
          repo: Repo
      ): F[List[Update.Single]] = {
        val commands =
          List(setDependencyUpdatesFailBuild, dependencyUpdates, reloadPlugins, dependencyUpdates)
        for {
          updates <- withTemporarySbtDependency(projectDir) {
            exec(sbtCmd(commands), projectDir).map(parser.parseSingleUpdates)
          }
          originalDependencies <- cacheRepository.getDependencies(List(repo))
          updatesUnderNewGroupId = originalDependencies.flatMap(
            UpdateAlg.findUpdateUnderNewGroup
          )
        } yield updates ++ updatesUnderNewGroupId
      }

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] =
        addGlobalPluginTemporarily(scalaStewardScalafixSbt) {
          for {
            repoDir <- workspaceAlg.repoDir(repo)
            scalafixCmds = for {
              migration <- migrations
              rule <- migration.rewriteRules
              cmd <- Nel.of(scalafix, testScalafix)
            } yield s"$cmd $rule"
            _ <- exec(sbtCmd("++2.12.10!" :: scalafixEnable :: scalafixCmds.toList), repoDir)
          } yield ()
        }

      val sbtDir: F[File] =
        fileAlg.home.map(_ / ".sbt")

      def exec(command: Nel[String], projectDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(projectDir)(processAlg.execSandboxed(command, projectDir))

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

      def withTemporarySbtDependency[A](projectDir: File)(fa: F[A]): F[A] =
        for {
          maybeSbtDep <- getSbtVersion(projectDir).map(
            _.flatMap(sbtDependency).map(_.formatAsModuleId)
          )
          maybeScalafmtDep <- scalafmtAlg
            .getScalafmtVersion(projectDir)
            .map(
              _.map(scalafmtDependency(defaultScalaBinaryVersion))
                .map(_.formatAsModuleIdScalaVersionAgnostic)
            )
          fakeDeps = Option(maybeSbtDep.toList ++ maybeScalafmtDep.toList).filter(_.nonEmpty)
          a <- fakeDeps.fold(fa) { dependencies =>
            val content = dependencies
              .map(dep => s"libraryDependencies += ${dep}")
              .mkString(System.lineSeparator())
            fileAlg.createTemporarily(projectDir / "project" / "tmp-sbt-dep.sbt", content)(fa)
          }
        } yield a
    }
}
