/*
 * Copyright 2018-2025 Scala Steward contributors
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

package org.scalasteward.core.buildtool.giter8

import cats.Monad
import cats.syntax.all.*
import org.scalasteward.core.buildtool.{BuildRoot, BuildToolCandidates}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.io.WorkspaceAlg

final class Giter8Alg[F[_]](implicit
    buildToolCandidates: BuildToolCandidates[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  private val giter8TemplateDir = "src/main/g8"

  def isGiter8Template(repo: Repo): F[Boolean] =
    workspaceAlg.repoDir(repo).map { repoDir =>
      repo.repo.endsWith(".g8") || (repoDir / giter8TemplateDir).isDirectory
    }

  def getGiter8BuildRoot(repo: Repo): F[Option[BuildRoot]] =
    for {
      isG8Template <- isGiter8Template(repo)
      buildRoot <- if (isG8Template) {
        val g8BuildRoot = BuildRoot(repo, giter8TemplateDir)
        buildToolCandidates.findBuildTools(g8BuildRoot).map { case (_, buildTools) =>
          if (buildTools.nonEmpty) Some(g8BuildRoot) else None
        }
      } else {
        F.pure(None)
      }
    } yield buildRoot
}

object Giter8Alg {
  def create[F[_]](implicit
      buildToolCandidates: BuildToolCandidates[F],
      workspaceAlg: WorkspaceAlg[F],
      F: Monad[F]
  ): Giter8Alg[F] = new Giter8Alg
}
