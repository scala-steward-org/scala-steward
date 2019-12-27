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

package org.scalasteward.core.maven

import better.files.File
import cats.Monad
import cats.implicits._
import org.scalasteward.core.application.Config
import org.scalasteward.core.build.system.BuildSystemAlg
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, RawUpdate, Update}
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

import scala.util.Try

object MavenAlg {

  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): BuildSystemAlg[F] =
    new BuildSystemAlg[F] {

      override def getDependencies(repo: Repo): F[List[Dependency]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          cmd = mvnCmd(command.Clean, command.mvnDepList)
          lines <- exec(cmd, repoDir)
          dependencies = parseDependencies(lines)
          //TODO: revisit scala check
          //          maybeScalafmtDependency <- scalafmtAlg.getScalafmtDependency(repo)
        } yield dependencies.distinct

      def exec(command: Nel[String], repoDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(repoDir)(processAlg.execSandboxed(command, repoDir))

      def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

      def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        fileAlg.removeTemporarily(dir / ".jvmopts") {
          fa
        }

      override def getUpdatesForRepo(repo: Repo): F[List[Update.Single]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          minorUpdatesRawLines <- exec(mvnCmd(command.MvnMinorUpdates), repoDir)
          minorUpdates = parseUpdates(minorUpdatesRawLines)
          majorUpdatesRawLines <- exec(mvnCmd(command.MvnDepUpdates), repoDir)
          majorUpdates = parseUpdates(majorUpdatesRawLines)
          pluginUpdatesRawLines <- exec(mvnCmd(command.PluginUpdates), repoDir)
          pluginUpdates = parseUpdates(pluginUpdatesRawLines)
          updates = groupNewerVersions(minorUpdates ++ majorUpdates)
          result = (updates ++ pluginUpdates)
            .map(_.toUpdate)
            .sortBy(update => (update.groupId, update.artifactId, update.currentVersion))

        } yield result

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] =
        F.unit //fixme:implement
    }

  private def mvnCmd(commands: String*): Nel[String] =
    Nel.of("mvn", commands.flatMap(_.split(" ")): _*)

  def removeNoise(s: String): String = s.replace("[INFO]", "").trim

  def parseDependencies(lines: List[String]): List[Dependency] = {
    val pattern = """(.*):(.*):jar:(.*):compile""".r
    lines
      .map(removeNoise)
      .map(_.trim)
      .map { s =>
        Try {
          val pattern(groupId, artifactIdCross, currentVersion) = s
          val artifactId = artifactIdCross.split("_") //fixme: check if it's scala binary
          new Dependency(
            GroupId(groupId),
            ArtifactId(artifactId(0), artifactIdCross),
            currentVersion
          )
        }.toOption // TODO: this doesn't catch exceptions thrown by Try, if any
      // TODO: does regex throw exceptions if there are no matches?
      // todo: add logger and log the exceptions
      }
      .collect { case Some(x) => x }
  }

  def groupNewerVersions(updates: List[RawUpdate]): List[RawUpdate] =
    updates
      .groupBy(_.dependency)
      .map {
        case (dependency, updates) =>
          val newUpdates: List[String] = updates.flatMap(_.newerVersions.toList).distinct
          Nel.fromList(newUpdates).map(RawUpdate(dependency, _))
      }
      .collect { case Some(x) => x }
      .toList

  def parseUpdates(lines: List[String]): List[RawUpdate] = {

    val pattern = """\s*([^:]+):([^ ]+)\s+(?:\.|\s)+([^ ]+)\s+->\s+(.*)$""".r
    lines
      .map(removeNoise)
      .map(_.trim)
      .map { s =>
        Try {
          val pattern(groupId, artifactIdCross, currentVersion, newVersion) = s
          val lastUScore = artifactIdCross.lastIndexOf('_')
          // fixme: make sure last segment actually looks like a scala binary version
          val artifactId =
            if (lastUScore >= 0) artifactIdCross.substring(0, lastUScore) else artifactIdCross
          val dependency = new Dependency(
            GroupId(groupId),
            ArtifactId(artifactId, artifactIdCross),
            currentVersion
          )
          val rawUpdate = RawUpdate(dependency, Nel.one[String](newVersion))
          rawUpdate
        }.toOption // TODO: this doesn't catch exceptions thrown by Try, if any
      // TODO: does regex throw exceptions if there are no matches?
      }
      .collect { case Some(x) => x }
  }
}
