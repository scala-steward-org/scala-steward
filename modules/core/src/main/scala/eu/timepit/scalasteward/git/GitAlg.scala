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

import better.files.File
import cats.effect.Sync
import cats.implicits._
import eu.timepit.scalasteward.application.WorkspaceAlg
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.{FileAlg, ProcessAlg}
import org.http4s.Uri

trait GitAlg[F[_]] {
  def checkout(repo: Repo, branch: Branch): F[Unit]

  def clone(repo: Repo, url: Uri): F[Unit]

  def push(repo: Repo, branch: Branch): F[Unit]

  def removeClone(repo: Repo): F[Unit]

  def syncFork(repo: Repo, upstreamUrl: Uri, defaultBranch: Branch): F[Unit]
}

object GitAlg {
  val gitCmd: String = "git"

  def sync[F[_]](
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F]
  )(implicit F: Sync[F]): GitAlg[F] =
    new GitAlg[F] {
      override def checkout(repo: Repo, branch: Branch): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(List("checkout", branch.name), repoDir)
        } yield ()

      override def clone(repo: Repo, url: Uri): F[Unit] =
        for {
          rootDir <- workspaceAlg.rootDir
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(List("clone", url.toString, repoDir.pathAsString), rootDir)
        } yield ()

      override def push(repo: Repo, branch: Branch): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(List("push", "--force", "--set-upstream", "origin", branch.name), repoDir)
        } yield ()

      override def removeClone(repo: Repo): F[Unit] =
        workspaceAlg.repoDir(repo).flatMap(fileAlg.deleteForce)

      override def syncFork(repo: Repo, upstreamUrl: Uri, defaultBranch: Branch): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          remoteName = "upstream"
          _ <- exec(List("remote", "add", remoteName, upstreamUrl.toString), repoDir)
          _ <- exec(List("fetch", remoteName), repoDir)
          _ <- checkout(repo, defaultBranch)
          _ <- exec(List("merge", s"$remoteName/${defaultBranch.name}"), repoDir)
          _ <- push(repo, defaultBranch)
        } yield ()

      def exec(command: List[String], cwd: File): F[List[String]] =
        processAlg.exec(gitCmd :: command, cwd)
    }
}
