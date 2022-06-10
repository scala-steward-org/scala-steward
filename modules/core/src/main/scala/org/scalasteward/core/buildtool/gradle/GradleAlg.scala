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

package org.scalasteward.core.buildtool.gradle

import better.files.File
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildToolAlg
import org.scalasteward.core.data._
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import cats.MonadError
import cats.Defer

trait GradleAlg[F[_]] extends BuildToolAlg[F] {
  def addDependencyTaskToBuild(repoDir: File): F[Unit]
  def removeDependencyTaskFromBuild(repoDir: File): F[Unit]
}

object GradleAlg {
  def create[F[_]](implicit
      config: Config,
      fileAlg: FileAlg[F],
      logger: Logger[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: MonadError[F, Throwable],
      D: Defer[F]
  ): GradleAlg[F] =
    new GradleAlg[F] {
      override def containsBuild(repo: Repo): F[Boolean] =
        workspaceAlg
          .repoDir(repo)
          .flatMap(repoDir => fileAlg.isRegularFile(repoDir / "build.gradle"))

      private val scalaStewardCommentHeader = "// --- scala-steward ---"
      override def addDependencyTaskToBuild(repoDir: File): F[Unit] = {
        val stewardDependenciesTaskDef =
          s"""$scalaStewardCommentHeader
             |allprojects {
             |    task stewardDependencies() {
             |        doLast {
             |            println("repositories")
             |            project.repositories { repositories ->
             |                repositories.forEach { repo ->
             |                    println("name: $${repo.name}")
             |                    println("url: $${repo.url}")
             |                }
             |            }
             |            println("dependency-lock-file")
             |            println(project.projectDir.toString() + "/dependencies.lock")
             |        }
             |    }
             |}""".stripMargin

        for {
          _ <- fileAlg.editFile(
            repoDir / "build.gradle",
            contents => Some(contents + System.lineSeparator + stewardDependenciesTaskDef)
          )
        } yield ()
      }

      override def removeDependencyTaskFromBuild(repoDir: File): F[Unit] =
        for {
          _ <- fileAlg.editFile(
            repoDir / "build.gradle",
            contents => Some(contents.split(scalaStewardCommentHeader)(0))
          )
        } yield ()

      override def getDependencies(repo: Repo): F[List[Scope.Dependencies]] =
        for {
          _ <- logger.info(s"Parsing Gradle dependencies and resolvers")
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- addDependencyTaskToBuild(repoDir)
          lines <- exec(gradleCmd("--offline", command.stewardDependencies), repoDir)
          dependencies <- parser.parseDependencies(lines)
          _ <- removeDependencyTaskFromBuild(repoDir)
        } yield dependencies

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] =
        for {
          _ <- logger.info(s"Running migrations $repo $migrations")
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(gradleCmd(command.stewardDependencies), repoDir)
        } yield ()

      def exec(command: Nel[String], repoDir: File): F[List[String]] =
        maybeIgnoreOptsFiles(repoDir)(processAlg.execSandboxed(command, repoDir))

      def gradleCmd(commands: String*): Nel[String] =
        Nel("./gradlew", commands.toList)

      def maybeIgnoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        if (config.ignoreOptsFiles) ignoreOptsFiles(dir)(fa) else fa

      def ignoreOptsFiles[A](dir: File)(fa: F[A]): F[A] =
        fileAlg.removeTemporarily(dir / ".jvmopts")(fa)
    }
}
