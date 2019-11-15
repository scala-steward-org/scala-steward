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

package org.scalasteward.core.io

import java.nio.file.PathMatcher

import better.files.File
import better.files.File.PathMatcherSyntax
import cats.FlatMap
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.vcs.data.Repo

trait WorkspaceAlg[F[_]] {
  def cleanWorkspace: F[Unit]

  def rootDir: F[File]

  def repoDir(repo: Repo): F[File]

  def findProjects(repo: Repo): F[List[File]]
}

object WorkspaceAlg {
  def create[F[_]](
      implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      config: Config,
      F: FlatMap[F]
  ): WorkspaceAlg[F] =
    new WorkspaceAlg[F] {
      private[this] val reposDir = config.workspace / "repos"
      private val pathMatcherFactory: String => PathMatcher =
        reposDir.pathMatcher(PathMatcherSyntax.glob, includePath = false)

      override def cleanWorkspace: F[Unit] =
        for {
          _ <- logger.info(s"Clean workspace ${config.workspace}")
          _ <- fileAlg.deleteForce(reposDir)
          _ <- rootDir
        } yield ()

      override def rootDir: F[File] =
        fileAlg.ensureExists(config.workspace)

      override def repoDir(repo: Repo): F[File] =
        fileAlg.ensureExists(reposDir / repo.owner / repo.repo)

      override def findProjects(repo: Repo): F[List[File]] =
        repoDir(repo).map { repoDir =>
          config.projectDirs match {
            case Nil => List(repoDir)
            case xs =>
              val escapedOwnerDir = PathMatcherSyntax.glob.escapePath(repoDir.parent.path.toString)
              val `/` = repoDir.fileSystem.getSeparator

              xs.flatMap { pattern =>
                  if (pattern === "**") {
                    findProjectsInOwnerDir(repo, "**", escapedOwnerDir, `/`)
                  } else if (pattern.startsWith(s"*${/}")) {
                    findProjectsInOwnerDir(
                      repo,
                      pattern.substring(s"*${/}".length),
                      escapedOwnerDir,
                      `/`
                    )
                  } else if (pattern.startsWith(s"**${/}")) {
                    glob(s"$escapedOwnerDir${/}$pattern${/}build.sbt")
                  } else if (pattern.startsWith(s"${repo.owner}${/}")) {
                    findProjectsInOwnerDir(
                      repo,
                      pattern.substring(s"${repo.owner}${/}".length),
                      escapedOwnerDir,
                      `/`
                    )
                  } else {
                    //ignore globs which are not for this repository
                    Iterable.empty
                  }
                }
                .map(_.parent)
          }
        }

      private def glob(pattern: String) =
        reposDir.glob(pattern, includePath = false)

      private def findProjectsInOwnerDir(
          repo: Repo,
          repoPattern: String,
          escapedOwnerDir: String,
          `/`: String
      ) = {
        val escapedOwnerRepoDir =
          s"$escapedOwnerDir${/}${PathMatcherSyntax.glob.escapePath(repo.repo)}"

        // special case `search everywhere`, we need to perform two searches because **/build.sbt would not
        // match build.sbt in root and **build.sbt would match also files like not-build.sbt
        if (repoPattern === "**") {
          val root = pathMatcherFactory(s"$escapedOwnerRepoDir${/}build.sbt")
          val subProjects =
            pathMatcherFactory(s"$escapedOwnerRepoDir${/}**${/}build.sbt")
          reposDir.collectChildren { file =>
            val path = file.path
            root.matches(path) || subProjects.matches(path)
          }
        } else if (repoPattern === "*") {
          glob(s"$escapedOwnerRepoDir${/}build.sbt")
        } else if (repoPattern.startsWith(s"*${/}")) {
          glob(
            s"$escapedOwnerRepoDir${/}${repoPattern.substring(/.length + 1)}${/}build.sbt"
          )
        } else if (repoPattern.startsWith(s"**${/}")) {
          glob(s"$escapedOwnerRepoDir${/}$repoPattern${/}build.sbt")
        } else if (repoPattern.startsWith(s"${repo.repo}${/}")) {
          glob(s"$escapedOwnerDir${/}$repoPattern${/}build.sbt")
        } else {
          //ignore globs which are not for this repository
          Iterable.empty
        }
      }
    }
}
