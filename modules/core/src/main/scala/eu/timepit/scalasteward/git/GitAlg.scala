/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.git

import cats.effect.IO
import cats.implicits._
import eu.timepit.scalasteward.application.WorkspaceAlg
import eu.timepit.scalasteward.gitLegacy
import eu.timepit.scalasteward.github.data.Repo
import org.http4s.Uri

trait GitAlg[F[_]] {
  def clone(repo: Repo, url: Uri): F[Unit]

  def removeClone(repo: Repo): F[Unit]

  def syncFork(repo: Repo, upstreamUrl: Uri): F[Unit]
}

class IoGitAlg(workspaceAlg: WorkspaceAlg[IO]) extends GitAlg[IO] {
  override def clone(repo: Repo, url: Uri): IO[Unit] =
    workspaceAlg.rootDir.flatMap { root =>
      workspaceAlg.repoDir(repo).flatMap { dir =>
        gitLegacy.clone(url, dir, root).void
      }
    }

  override def removeClone(repo: Repo): IO[Unit] = IO.unit

  override def syncFork(repo: Repo, upstreamUrl: Uri): IO[Unit] = IO.unit
}
