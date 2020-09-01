/*
 * Copyright 2018-2020 Scala Steward contributors
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

package org.scalasteward.core.buildtool.maven

import better.files.File
import cats.Monad
import cats.implicits._
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildToolAlg
import org.scalasteward.core.data._
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

trait MavenAlg[F[_]] extends BuildToolAlg[F]

object MavenAlg {
  def create[F[_]](implicit
      config: Config,
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): MavenAlg[F] =
    new MavenAlg[F] {
      override def containsBuild(repo: Repo): F[Boolean] =
        workspaceAlg.repoDir(repo).flatMap(repoDir => fileAlg.isRegularFile(repoDir / "pom.xml"))

      override def getDependencies(repo: Repo): F[List[Scope.Dependencies]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          dependenciesRaw <- exec(mvnCmd(command.listDependencies), repoDir)
          repositoriesRaw <- exec(mvnCmd(command.listRepositories), repoDir)
          dependencies = parser.parseDependencies(dependenciesRaw).distinct
          resolvers = parser.parseResolvers(repositoriesRaw).distinct
        } yield List(Scope(dependencies, resolvers))

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] =
        F.unit

      def exec(command: Nel[String], repoDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(repoDir)(processAlg.execSandboxed(command, repoDir))

      def mvnCmd(commands: String*): Nel[String] =
        Nel("mvn", "--batch-mode" :: commands.toList)

      def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

      def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        fileAlg.removeTemporarily(dir / ".jvmopts")(fa)
    }
}
