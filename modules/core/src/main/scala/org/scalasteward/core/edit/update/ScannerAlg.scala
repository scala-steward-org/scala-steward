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

package org.scalasteward.core.edit.update

import better.files.File
import cats.effect.Concurrent
import cats.syntax.functor._
import com.github.arturopala.gitignore.GitIgnore
import fs2.Stream
import org.scalasteward.core.data.{Dependency, Repo, Version}
import org.scalasteward.core.edit.update.data.{ModulePosition, VersionPosition}
import org.scalasteward.core.io.{FileAlg, FileData, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel

/** Scans all files that Scala Steward is allowed to edit for version and module positions. */
final class ScannerAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Concurrent[F]
) {
  def findVersionPositions(
      repo: Repo,
      config: RepoConfig,
      version: Version
  ): F[List[VersionPosition]] =
    findPathsContaining(repo, config, version.value)
      .map(VersionPositionScanner.findPositions(version, _))
      .compile
      .foldMonoid

  def findModulePositions(
      repo: Repo,
      config: RepoConfig,
      dependencies: Nel[Dependency]
  ): F[List[ModulePosition]] =
    findPathsContaining(repo, config, dependencies.head.groupId.value)
      .map { fileData =>
        dependencies.toList.flatMap { dependency =>
          if (!fileData.content.contains(dependency.artifactId.name)) List.empty
          else ModulePositionScanner.findPositions(dependency, fileData)
        }
      }
      .compile
      .foldMonoid

  private def getGitIgnore(repoDir: File): F[Option[GitIgnore]] = {
    val gitIgnore = repoDir / ".gitignore"
    fileAlg.readFile(gitIgnore).map(_.map(GitIgnore.parse))
  }

  private def findPathsContaining(
      repo: Repo,
      config: RepoConfig,
      string: String
  ): Stream[F, FileData] = {
    def fileFilter(repoDir: File, maybeGitIgnore: Option[GitIgnore]) = (file: File) => {
      val path = repoDir.relativize(file).toString
      val notDotGit = !path.startsWith(".git/")
      val onlyKeepConfiguredExtensions =
        config.updates.fileExtensionsOrDefault.exists(path.endsWith)
      val allowedByGitIgnore = maybeGitIgnore.map(_.isAllowed(path)).getOrElse(true)
      val cond = notDotGit &&
        onlyKeepConfiguredExtensions &&
        allowedByGitIgnore
      Option.when(cond)(path)
    }
    val contentFilter = (content: String) => Some(content).filter(_.contains(string))

    for {
      repoDir <- Stream.eval(workspaceAlg.repoDir(repo))
      maybeRootGitIgnore <- Stream.eval(getGitIgnore(repoDir))
      files <- fileAlg
        .findFiles(repoDir, fileFilter(repoDir, maybeRootGitIgnore), contentFilter)
        .map(FileData.tupled)
    } yield files
  }
}
