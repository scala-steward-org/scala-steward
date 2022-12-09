/*
 * Copyright 2018-2022 Scala Steward contributors
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
import cats.effect.kernel.Concurrent
import cats.syntax.all._
import fs2.Stream
import org.scalasteward.core.data.{Dependency, Version}
import org.scalasteward.core.edit.update.data.{ModulePosition, PathList, VersionPosition}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

final class ScannerAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Concurrent[F]
) {
  def findVersionPositions(
      repo: Repo,
      config: RepoConfig,
      version: Version
  ): F[PathList[List[VersionPosition]]] =
    findPathsContaining(repo, config, version.value)
      .mapFilter { case (path, content) =>
        val positions = VersionScanner.findPositions(version, content)
        Option.when(positions.nonEmpty)(path -> positions)
      }
      .compile
      .toList

  def findModulePositions(
      repo: Repo,
      config: RepoConfig,
      dependencies: Nel[Dependency]
  ): F[PathList[List[ModulePosition]]] =
    findPathsContaining(repo, config, dependencies.head.groupId.value)
      .mapFilter { case (path, content) =>
        val positions = dependencies.toList.flatMap { dependency =>
          if (!content.contains(dependency.artifactId.name)) List.empty
          else ModuleScanner.findPositions(dependency, content)
        }
        Option.when(positions.nonEmpty)(path -> positions)
      }
      .compile
      .toList

  private def findPathsContaining(
      repo: Repo,
      config: RepoConfig,
      string: String
  ): Stream[F, (String, String)] =
    Stream.eval(workspaceAlg.repoDir(repo)).flatMap { repoDir =>
      def pathOf(file: File) = repoDir.relativize(file).toString
      val fileFilter =
        (file: File) => {
          val path = pathOf(file)
          config.updates.fileExtensionsOrDefault.exists(path.endsWith)
        }
      fileAlg
        .findFiles(repoDir, fileFilter, _.contains(string))
        .map { case (file, content) => (pathOf(file), content) }
    }
}
