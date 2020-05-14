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

package org.scalasteward.core.buildsystem.maven

import better.files.File
import cats.Monad
import cats.implicits._
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildsystem.BuildSystemAlg
import org.scalasteward.core.data._
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

trait MavenAlg[F[_]] extends BuildSystemAlg[F]

object MavenAlg {
  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): MavenAlg[F] = new MavenAlg[F] {
    override def containsBuild(repo: Repo): F[Boolean] =
      workspaceAlg.repoDir(repo).flatMap(repoDir => fileAlg.isRegularFile(repoDir / "pom.xml"))

    override def getDependencies(repo: Repo): F[List[Scope.Dependencies]] =
      for {
        repoDir <- workspaceAlg.repoDir(repo)
        listDependenciesCommand = mvnCmd(command.listDependencies)
        listResolversCommand = mvnCmd(command.listRepositories)
        repositoriesRaw <- exec(listResolversCommand, repoDir)
        dependenciesRaw <- exec(listDependenciesCommand, repoDir)
        (_, dependencies) = MavenParser.parseAllDependencies(dependenciesRaw)
        (_, resolvers) = MavenParser.parseResolvers(repositoriesRaw.mkString("\n"))
      } yield {
        val deps = dependencies.distinct
        List(Scope(deps, resolvers))
      }

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
