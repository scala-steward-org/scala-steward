/*
 * Copyright 2018-2021 Scala Steward contributors
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

import cats.effect.{Bracket, BracketThrow}
import cats.syntax.all._
import cats.{FlatMap, Monad}
import org.http4s.Uri
import org.scalasteward.core.application.Config.GitCfg
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}

trait GenGitAlg[F[_], Repo] {
  def branchAuthors(repo: Repo, branch: Branch, base: Branch): F[List[String]]

  def checkoutBranch(repo: Repo, branch: Branch): F[Unit]

  def clone(repo: Repo, url: Uri): F[Unit]

  def cloneExists(repo: Repo): F[Boolean]

  def commitAll(repo: Repo, message: String, messages: String*): F[Commit]

  def containsChanges(repo: Repo): F[Boolean]

  def createBranch(repo: Repo, branch: Branch): F[Unit]

  def removeBranch(repo: Repo, branch: Branch): F[Unit]

  def currentBranch(repo: Repo): F[Branch]

  /** Discards unstaged changes. */
  def discardChanges(repo: Repo): F[Unit]

  def findFilesContaining(repo: Repo, string: String): F[List[String]]

  /** Returns `true` if merging `branch` into `base` results in merge conflicts. */
  def hasConflicts(repo: Repo, branch: Branch, base: Branch): F[Boolean]

  def initSubmodules(repo: Repo): F[Unit]

  def isMerged(repo: Repo, branch: Branch, base: Branch): F[Boolean]

  def latestSha1(repo: Repo, branch: Branch): F[Sha1]

  /** Merges `branch` into the current branch using `theirs` as merge strategy option. */
  def mergeTheirs(repo: Repo, branch: Branch): F[Option[Commit]]

  def push(repo: Repo, branch: Branch): F[Unit]

  def removeClone(repo: Repo): F[Unit]

  def setAuthor(repo: Repo, author: Author): F[Unit]

  def syncFork(repo: Repo, upstreamUrl: Uri, defaultBranch: Branch): F[Unit]

  def version: F[String]

  final def commitAllIfDirty(repo: Repo, message: String, messages: String*)(implicit
      F: Monad[F]
  ): F[Option[Commit]] =
    containsChanges(repo).ifM(commitAll(repo, message, messages: _*).map(Some.apply), F.pure(None))

  final def returnToCurrentBranch[A, E](repo: Repo)(fa: F[A])(implicit F: Bracket[F, E]): F[A] =
    F.bracket(currentBranch(repo))(_ => fa)(checkoutBranch(repo, _))

  final def contramapRepoF[A](f: A => F[Repo])(implicit F: FlatMap[F]): GenGitAlg[F, A] = {
    val self = this
    new GenGitAlg[F, A] {
      override def branchAuthors(repo: A, branch: Branch, base: Branch): F[List[String]] =
        f(repo).flatMap(self.branchAuthors(_, branch, base))

      override def checkoutBranch(repo: A, branch: Branch): F[Unit] =
        f(repo).flatMap(self.checkoutBranch(_, branch))

      override def clone(repo: A, url: Uri): F[Unit] =
        f(repo).flatMap(self.clone(_, url))

      override def cloneExists(repo: A): F[Boolean] =
        f(repo).flatMap(self.cloneExists)

      override def commitAll(repo: A, message: String, messages: String*): F[Commit] =
        f(repo).flatMap(self.commitAll(_, message, messages: _*))

      override def containsChanges(repo: A): F[Boolean] =
        f(repo).flatMap(self.containsChanges)

      override def createBranch(repo: A, branch: Branch): F[Unit] =
        f(repo).flatMap(self.createBranch(_, branch))

      override def removeBranch(repo: A, branch: Branch): F[Unit] =
        f(repo).flatMap(self.removeBranch(_, branch))

      override def currentBranch(repo: A): F[Branch] =
        f(repo).flatMap(self.currentBranch)

      override def discardChanges(repo: A): F[Unit] =
        f(repo).flatMap(self.discardChanges)

      override def findFilesContaining(repo: A, string: String): F[List[String]] =
        f(repo).flatMap(self.findFilesContaining(_, string))

      override def hasConflicts(repo: A, branch: Branch, base: Branch): F[Boolean] =
        f(repo).flatMap(self.hasConflicts(_, branch, base))

      override def initSubmodules(repo: A): F[Unit] =
        f(repo).flatMap(self.initSubmodules)

      override def isMerged(repo: A, branch: Branch, base: Branch): F[Boolean] =
        f(repo).flatMap(self.isMerged(_, branch, base))

      override def latestSha1(repo: A, branch: Branch): F[Sha1] =
        f(repo).flatMap(self.latestSha1(_, branch))

      override def mergeTheirs(repo: A, branch: Branch): F[Option[Commit]] =
        f(repo).flatMap(self.mergeTheirs(_, branch))

      override def push(repo: A, branch: Branch): F[Unit] =
        f(repo).flatMap(self.push(_, branch))

      override def removeClone(repo: A): F[Unit] =
        f(repo).flatMap(self.removeClone)

      override def setAuthor(repo: A, author: Author): F[Unit] =
        f(repo).flatMap(self.setAuthor(_, author))

      override def syncFork(repo: A, upstreamUrl: Uri, defaultBranch: Branch): F[Unit] =
        f(repo).flatMap(self.syncFork(_, upstreamUrl, defaultBranch))

      override def version: F[String] =
        self.version
    }
  }
}

object GenGitAlg {
  def create[F[_]](config: GitCfg)(implicit
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: BracketThrow[F]
  ): GitAlg[F] =
    new FileGitAlg[F](config).contramapRepoF(workspaceAlg.repoDir)
}
