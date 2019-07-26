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

package org.scalasteward.core.git

import better.files.File
import cats.effect.Bracket
import cats.implicits._
import org.http4s.Uri
import org.scalasteward.core.application.Config
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.{BracketThrowable, Nel}
import org.scalasteward.core.vcs.data.Repo

trait GitAlg[F[_]] {
  def branchAuthors(repo: Repo, branch: Branch, base: Branch): F[List[String]]

  def checkoutBranch(repo: Repo, branch: Branch): F[Unit]

  def clone(repo: Repo, url: Uri): F[Unit]

  def commitAll(repo: Repo, message: String): F[Unit]

  def containsChanges(repo: Repo): F[Boolean]

  def createBranch(repo: Repo, branch: Branch): F[Unit]

  def currentBranch(repo: Repo): F[Branch]

  /** Returns `true` if merging `branch` into `base` results in merge conflicts. */
  def hasConflicts(repo: Repo, branch: Branch, base: Branch): F[Boolean]

  def isMerged(repo: Repo, branch: Branch, base: Branch): F[Boolean]

  def latestSha1(repo: Repo, branch: Branch): F[Sha1]

  /** Merges `branch` into the current branch using `theirs` as merge strategy option. */
  def mergeTheirs(repo: Repo, branch: Branch): F[Unit]

  def forcePush(repo: Repo, branch: Branch): F[Unit]

  def remoteBranchExists(repo: Repo, branch: Branch): F[Boolean]

  def removeClone(repo: Repo): F[Unit]

  def setAuthor(repo: Repo, author: Author): F[Unit]

  def syncFork(repo: Repo, upstreamUrl: Uri, defaultBranch: Branch): F[Unit]

  final def returnToCurrentBranch[A, E](repo: Repo)(fa: F[A])(implicit F: Bracket[F, E]): F[A] =
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
      F: BracketThrowable[F]
  ): GitAlg[F] =
    new GitAlg[F] {
      override def branchAuthors(repo: Repo, branch: Branch, base: Branch): F[List[String]] =
        execFromRepo(Nel.of("log", "--pretty=format:'%an'", dotdot(base, branch)), repo)

      override def checkoutBranch(repo: Repo, branch: Branch): F[Unit] =
        execFromRepo_(Nel.of("checkout", branch.name), repo)

      override def clone(repo: Repo, url: Uri): F[Unit] =
        for {
          rootDir <- workspaceAlg.rootDir
          repoDir <- workspaceAlg.repoDir(repo)
          _ <- exec(Nel.of("clone", "--recursive", url.toString, repoDir.pathAsString), rootDir)
        } yield ()

      override def commitAll(repo: Repo, message: String): F[Unit] = {
        val sign = if (config.signCommits) "--gpg-sign" else "--no-gpg-sign"
        execFromRepo_(Nel.of("commit", "--all", "-m", message, sign), repo)
      }

      override def containsChanges(repo: Repo): F[Boolean] =
        execFromRepo(Nel.of("status", "--porcelain", "--untracked-files=no"), repo).map(_.nonEmpty)

      override def createBranch(repo: Repo, branch: Branch): F[Unit] =
        execFromRepo_(Nel.of("checkout", "-b", branch.name), repo)

      override def currentBranch(repo: Repo): F[Branch] =
        for {
          lines <- execFromRepo(Nel.of("rev-parse", "--abbrev-ref", "HEAD"), repo)
        } yield Branch(lines.mkString.trim)

      override def hasConflicts(repo: Repo, branch: Branch, base: Branch): F[Boolean] =
        workspaceAlg.repoDir(repo).flatMap { repoDir =>
          val tryMerge = exec(Nel.of("merge", "--no-commit", "--no-ff", branch.name), repoDir)
          val abortMerge = exec(Nel.of("merge", "--abort"), repoDir).void

          returnToCurrentBranch(repo) {
            checkoutBranch(repo, base) >> F.guarantee(tryMerge)(abortMerge).attempt.map(_.isLeft)
          }
        }

      override def isMerged(repo: Repo, branch: Branch, base: Branch): F[Boolean] =
        execFromRepo(Nel.of("log", "--pretty=format:'%h'", dotdot(base, branch)), repo)
          .map(_.isEmpty)

      override def latestSha1(repo: Repo, branch: Branch): F[Sha1] =
        for {
          lines <- execFromRepo(Nel.of("rev-parse", "--verify", branch.name), repo)
          sha1 <- F.fromEither(Sha1.from(lines.mkString("").trim))
        } yield sha1

      override def mergeTheirs(repo: Repo, branch: Branch): F[Unit] = {
        val sign = if (config.signCommits) "--gpg-sign" else "--no-gpg-sign"
        execFromRepo_(Nel.of("merge", "--strategy-option=theirs", sign, branch.name), repo)
      }

      override def forcePush(repo: Repo, branch: Branch): F[Unit] =
        execFromRepo_(Nel.of("push", "--force", "--set-upstream", "origin", branch.name), repo)

      override def remoteBranchExists(repo: Repo, branch: Branch): F[Boolean] =
        for {
          branches <- execFromRepo(Nel.of("branch", "-r"), repo)
        } yield branches.exists(_.endsWith(branch.name))

      override def removeClone(repo: Repo): F[Unit] =
        workspaceAlg.repoDir(repo).flatMap(fileAlg.deleteForce)

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
          _ <- forcePush(repo, defaultBranch)
        } yield ()

      def execFromRepo_(command: Nel[String], repo: Repo): F[Unit] =
        execFromRepo(command, repo).as(())

      def execFromRepo(command: Nel[String], repo: Repo): F[List[String]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          result <- exec(command, repoDir)
        } yield result

      def exec(command: Nel[String], cwd: File): F[List[String]] =
        processAlg.exec(gitCmd :: command, cwd, "GIT_ASKPASS" -> config.gitAskPass.pathAsString)
    }
}
