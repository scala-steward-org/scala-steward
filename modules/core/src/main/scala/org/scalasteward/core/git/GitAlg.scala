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

package org.scalasteward.core.git

import cats.FlatMap
import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.io.WorkspaceAlg
import org.scalasteward.core.vcs.data.Repo

final class GitAlg[F[_]](implicit
    fileGitAlg: FileGitAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: FlatMap[F]
) extends GenGitAlg[F, Repo] {
  override def branchAuthors(repo: Repo, branch: Branch, base: Branch): F[List[String]] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.branchAuthors(_, branch, base))

  override def checkoutBranch(repo: Repo, branch: Branch): F[Unit] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.checkoutBranch(_, branch))

  override def clone(repo: Repo, url: Uri): F[Unit] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.clone(_, url))

  override def cloneExists(repo: Repo): F[Boolean] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.cloneExists)

  override def commitAll(repo: Repo, message: String): F[Commit] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.commitAll(_, message))

  override def containsChanges(repo: Repo): F[Boolean] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.containsChanges)

  override def createBranch(repo: Repo, branch: Branch): F[Unit] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.createBranch(_, branch))

  override def currentBranch(repo: Repo): F[Branch] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.currentBranch)

  override def findFilesContaining(repo: Repo, string: String): F[List[String]] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.findFilesContaining(_, string))

  override def hasConflicts(repo: Repo, branch: Branch, base: Branch): F[Boolean] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.hasConflicts(_, branch, base))

  override def isMerged(repo: Repo, branch: Branch, base: Branch): F[Boolean] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.isMerged(_, branch, base))

  override def latestSha1(repo: Repo, branch: Branch): F[Sha1] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.latestSha1(_, branch))

  override def mergeTheirs(repo: Repo, branch: Branch): F[Option[Commit]] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.mergeTheirs(_, branch))

  override def push(repo: Repo, branch: Branch): F[Unit] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.push(_, branch))

  override def removeClone(repo: Repo): F[Unit] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.removeClone)

  override def setAuthor(repo: Repo, author: Author): F[Unit] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.setAuthor(_, author))

  override def syncFork(repo: Repo, upstreamUrl: Uri, defaultBranch: Branch): F[Unit] =
    workspaceAlg.repoDir(repo).flatMap(fileGitAlg.syncFork(_, upstreamUrl, defaultBranch))
}
