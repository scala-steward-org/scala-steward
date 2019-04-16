/*
 * Copyright 2018-2019 scala-steward contributors
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
import org.scalasteward.core.dependency.Dependency
import org.scalasteward.core.dependency.parser.parseDependencies
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.io.{FileAlg, FileData, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.model.Update
import org.scalasteward.core.sbt.command._
import org.scalasteward.core.sbt.data.ArtificialProject
import org.scalasteward.core.util.Nel

trait SbtAlg[F[_]] {
  def addGlobalPlugin(plugin: FileData): F[Unit]

  def addGlobalPlugins: F[Unit]

  def getDependencies(repo: Repo): F[List[Dependency]]

  def getUpdatesForProject(project: ArtificialProject): F[List[Update.Single]]

  def getUpdatesForRepo(repo: Repo): F[List[Update.Single]]
}

object SbtAlg {
  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      logger: Logger[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): SbtAlg[F] =
    new SbtAlg[F] {
      override def addGlobalPlugin(plugin: FileData): F[Unit] =
        List("0.13", "1.0").traverse_ { series =>
          sbtDir.flatMap(dir => fileAlg.writeFileData(dir / series / "plugins", plugin))
        }

      override def addGlobalPlugins: F[Unit] =
        for {
          _ <- logger.info("Add global sbt plugins")
          _ <- addGlobalPlugin(sbtUpdatesPlugin)
          _ <- addGlobalPlugin(stewardPlugin)
        } yield ()

      override def getDependencies(repo: Repo): F[List[Dependency]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          cmd = sbtCmd(List(libraryDependenciesAsJson, reloadPlugins, libraryDependenciesAsJson))
          lines <- exec(cmd, repoDir)
        } yield lines.flatMap(parseDependencies).distinct

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

      override def getUpdatesForRepo(repo: Repo): F[List[Update.Single]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          maybeClearCredentials = if (config.keepCredentials) Nil else List(setCredentialsToNil)
          commands = maybeClearCredentials ++
            List(dependencyUpdates, reloadPlugins, dependencyUpdates)
          lines <- exec(sbtCmd(commands), repoDir)
        } yield parser.parseSingleUpdates(lines)

      val sbtDir: F[File] =
        fileAlg.home.map(_ / ".sbt")

      def exec(command: Nel[String], repoDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(repoDir)(processAlg.execSandboxed(command, repoDir))

      def sbtCmd(commands: List[String]): Nel[String] =
        Nel.of("sbt", "-batch", "-no-colors", commands.mkString(";", ";", ""))

      def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

      def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] = {
        val jvmopts = ".jvmopts"
        fileAlg.removeTemporarily(dir / jvmopts) {
          fileAlg.removeTemporarily(dir / ".sbtopts") {
            fileAlg.createTemporarily(dir / jvmopts, "-Xss8m") {
              fa
            }
          }
        }
      }
    }
}
