/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.git

import better.files.File
import cats.effect.Bracket
import cats.implicits._
import org.http4s.Uri
import org.scalasteward.core.application.Config
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.{MonadThrowable, Nel}

trait GitAlg[F[_]] {
  def branchAuthors(repo: Repo, branch: Branch, base: Branch): F[List[String]]

  def checkoutBranch(repo: Repo, branch: Branch): F[Unit]

  def clone(repo: Repo, url: Uri): F[Unit]

  def commitAll(repo: Repo, message: String): F[Unit]

  def containsChanges(repo: Repo): F[Boolean]

  def createBranch(repo: Repo, branch: Branch): F[Unit]

  def currentBranch(repo: Repo): F[Branch]

  def isBehind(repo: Repo, branch: Branch, base: Branch): F[Boolean]

  def isMerged(repo: Repo, branch: Branch, base: Branch): F[Boolean]

  def latestSha1(repo: Repo, branch: Branch): F[Sha1]

  def push(repo: Repo, branch: Branch): F[Unit]

  def remoteBranchExists(repo: Repo, branch: Branch): F[Boolean]

  def removeClone(repo: Repo): F[Unit]

  def resetHard(repo: Repo, base: Branch): F[Unit]

  def setAuthor(repo: Repo, author: Author): F[Unit]

  def syncFork(repo: Repo, upstreamUrl: Uri, defaultBranch: Branch): F[Unit]

  def returnToCurrentBranch[A, E](repo: Repo)(fa: F[A])(implicit F: Bracket[F, E]): F[A] =
    F.bracket(currentBranch(repo))(_ => fa)(checkoutBranch(repo, _))
}

object GitAlg {
  val gitCmd: String = "git"

  def create[F[_]](
      implicit
      config: Config,
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: MonadThrowable[F]
  ): GitAlg[F] =
    new GitAlg[F] {
      override def branchAuthors(repo: Repo, branch: Branch, base: Branch): F[List[String]] =
        workspaceAlg.repoDir(repo).flatMap { repoDir =>
          exec(Nel.of("log", "--pretty=format:'%an'", dotdot(base, branch)), repoDir)
        }

      override def checkoutBranch(repo: Repo, branch: Branch): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(Nel.of("checkout", branch.name), repoDir)
        } yield ()

      override def clone(repo: Repo, url: Uri): F[Unit] =
        for {
          rootDir <- workspaceAlg.rootDir
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(Nel.of("clone", "--recursive", url.toString, repoDir.pathAsString), rootDir)
        } yield ()

      override def commitAll(repo: Repo, message: String): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          sign = if (config.signCommits) List("--gpg-sign") else List("--no-gpg-sign")
          _ <- exec(Nel.of("commit", "--all", "-m", message) ++ sign, repoDir)
        } yield ()

      override def containsChanges(repo: Repo): F[Boolean] =
        workspaceAlg.repoDir(repo).flatMap { repoDir =>
          exec(Nel.of("status", "--porcelain"), repoDir).map(_.nonEmpty)
        }

      override def createBranch(repo: Repo, branch: Branch): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(Nel.of("checkout", "-b", branch.name), repoDir)
        } yield ()

      override def currentBranch(repo: Repo): F[Branch] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          lines <- exec(Nel.of("rev-parse", "--abbrev-ref", "HEAD"), repoDir)
        } yield Branch(lines.mkString.trim)

      override def isBehind(repo: Repo, branch: Branch, base: Branch): F[Boolean] =
        workspaceAlg.repoDir(repo).flatMap { repoDir =>
          exec(Nel.of("log", "--pretty=format:'%h'", dotdot(branch, base)), repoDir).map(_.nonEmpty)
        }

      override def isMerged(repo: Repo, branch: Branch, base: Branch): F[Boolean] =
        workspaceAlg.repoDir(repo).flatMap { repoDir =>
          exec(Nel.of("log", "--pretty=format:'%h'", dotdot(base, branch)), repoDir).map(_.isEmpty)
        }

      override def latestSha1(repo: Repo, branch: Branch): F[Sha1] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          lines <- exec(Nel.of("rev-parse", "--verify", branch.name), repoDir)
          sha1 <- F.fromEither(Sha1.from(lines.mkString("").trim))
        } yield sha1

      override def push(repo: Repo, branch: Branch): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(Nel.of("push", "--force", "--set-upstream", "origin", branch.name), repoDir)
        } yield ()

      override def remoteBranchExists(repo: Repo, branch: Branch): F[Boolean] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          branches <- exec(Nel.of("branch", "-r"), repoDir)
        } yield branches.exists(_.endsWith(branch.name))

      override def removeClone(repo: Repo): F[Unit] =
        workspaceAlg.repoDir(repo).flatMap(fileAlg.deleteForce)

      override def resetHard(repo: Repo, base: Branch): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(Nel.of("reset", "--hard", base.name), repoDir)
        } yield ()

      override def setAuthor(repo: Repo, author: Author): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(Nel.of("config", "user.email", author.email), repoDir)
          _ <- exec(Nel.of("config", "user.name", author.name), repoDir)
        } yield ()

      override def syncFork(repo: Repo, upstreamUrl: Uri, defaultBranch: Branch): F[Unit] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          remote = "upstream"
          branch = defaultBranch.name
          remoteBranch = s"$remote/$branch"
          _ <- exec(Nel.of("remote", "add", remote, upstreamUrl.toString), repoDir)
          _ <- exec(Nel.of("fetch", remote), repoDir)
          _ <- exec(Nel.of("checkout", "-B", branch, "--track", remoteBranch), repoDir)
          _ <- exec(Nel.of("merge", remoteBranch), repoDir)
          _ <- push(repo, defaultBranch)
        } yield ()

      def exec(command: Nel[String], cwd: File): F[List[String]] =
        processAlg.exec(gitCmd :: command, cwd, "GIT_ASKPASS" -> config.gitAskPass.pathAsString)
    }
}
