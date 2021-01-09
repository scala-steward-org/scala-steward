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

import better.files.File
import cats.effect.BracketThrow
import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.application.Config.GitCfg
import org.scalasteward.core.git.FileGitAlg.dotdot
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel

final class FileGitAlg[F[_]](config: GitCfg)(implicit
    fileAlg: FileAlg[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: BracketThrow[F]
) extends GenGitAlg[F, File] {
  override def branchAuthors(repo: File, branch: Branch, base: Branch): F[List[String]] =
    git("log", "--pretty=format:'%an'", dotdot(base, branch))(repo)

  override def checkoutBranch(repo: File, branch: Branch): F[Unit] =
    git("checkout", branch.name)(repo).void

  override def clone(repo: File, url: Uri): F[Unit] =
    for {
      rootDir <- workspaceAlg.rootDir
      _ <- git("clone", url.toString, repo.pathAsString)(rootDir)
    } yield ()

  override def cloneExists(repo: File): F[Boolean] =
    fileAlg.isDirectory(repo / ".git")

  override def commitAll(repo: File, message: String, messages: String*): F[Commit] = {
    val allMessages = (message :: messages.toList).foldMap(m => List("-m", m))
    git("commit" :: "--all" :: sign :: allMessages: _*)(repo).as(Commit())
  }

  override def containsChanges(repo: File): F[Boolean] =
    git("status", "--porcelain", "--untracked-files=no", "--ignore-submodules")(repo)
      .map(_.nonEmpty)

  override def createBranch(repo: File, branch: Branch): F[Unit] =
    git("checkout", "-b", branch.name)(repo).void

  override def removeBranch(repo: File, branch: Branch): F[Unit] =
    git("push", "origin", "--delete", branch.name)(repo).void

  override def currentBranch(repo: File): F[Branch] =
    git("rev-parse", "--abbrev-ref", Branch.head.name)(repo)
      .map(lines => Branch(lines.mkString.trim))

  override def discardChanges(repo: File): F[Unit] =
    git("checkout", "--", ".")(repo).void

  override def findFilesContaining(repo: File, string: String): F[List[String]] =
    git("grep", "-I", "--fixed-strings", "--files-with-matches", string)(repo)
      .handleError(_ => List.empty[String])
      .map(_.filter(_.nonEmpty))

  override def hasConflicts(repo: File, branch: Branch, base: Branch): F[Boolean] = {
    val tryMerge = git("merge", "--no-commit", "--no-ff", branch.name)(repo)
    val abortMerge = git("merge", "--abort")(repo).void

    returnToCurrentBranch(repo) {
      checkoutBranch(repo, base) >> F.guarantee(tryMerge)(abortMerge).attempt.map(_.isLeft)
    }
  }

  override def initSubmodules(repo: File): F[Unit] =
    git("submodule", "update", "--init", "--recursive")(repo).void

  override def isMerged(repo: File, branch: Branch, base: Branch): F[Boolean] =
    git("log", "--pretty=format:'%h'", dotdot(base, branch))(repo).map(_.isEmpty)

  override def latestSha1(repo: File, branch: Branch): F[Sha1] =
    git("rev-parse", "--verify", branch.name)(repo)
      .flatMap(lines => F.fromEither(Sha1.from(lines.mkString("").trim)))

  override def mergeTheirs(repo: File, branch: Branch): F[Option[Commit]] =
    for {
      before <- latestSha1(repo, Branch.head)
      _ <- git("merge", "--strategy-option=theirs", sign, branch.name)(repo).void
        .handleErrorWith { throwable =>
          // Resolve CONFLICT (modify/delete) by deleting unmerged files:
          for {
            unmergedFiles <- git("diff", "--name-only", "--diff-filter=U")(repo)
            _ <- Nel
              .fromList(unmergedFiles.filter(_.nonEmpty))
              .fold(F.raiseError[Unit](throwable))(_.traverse_(file => git("rm", file)(repo)))
            _ <- git("commit", "--all", "--no-edit", sign)(repo)
          } yield ()
        }
      after <- latestSha1(repo, Branch.head)
    } yield Option.when(before =!= after)(Commit())

  override def push(repo: File, branch: Branch): F[Unit] =
    git("push", "--force", "--set-upstream", "origin", branch.name)(repo).void

  override def removeClone(repo: File): F[Unit] =
    fileAlg.deleteForce(repo)

  override def setAuthor(repo: File, author: Author): F[Unit] =
    for {
      _ <- git("config", "user.email", author.email)(repo)
      _ <- git("config", "user.name", author.name)(repo)
      _ <- author.signingKey.traverse_(key => git("config", "user.signingKey", key)(repo))
    } yield ()

  override def syncFork(repo: File, upstreamUrl: Uri, defaultBranch: Branch): F[Unit] =
    for {
      _ <- F.unit
      remote = "upstream"
      branch = defaultBranch.name
      remoteBranch = s"$remote/$branch"
      _ <- git("remote", "add", remote, upstreamUrl.toString)(repo)
      _ <- git("fetch", "--force", "--tags", remote, branch)(repo)
      _ <- git("checkout", "-B", branch, "--track", remoteBranch)(repo)
      _ <- git("merge", remoteBranch)(repo)
      _ <- push(repo, defaultBranch)
    } yield ()

  override def version: F[String] =
    workspaceAlg.rootDir.flatMap(git("--version")).map(_.mkString.trim)

  private def git(args: String*)(repo: File): F[List[String]] =
    processAlg.exec(Nel.of("git", args: _*), repo, "GIT_ASKPASS" -> config.gitAskPass.pathAsString)

  private val sign: String =
    if (config.signCommits) "--gpg-sign" else "--no-gpg-sign"
}

object FileGitAlg {
  // man 7 gitrevisions:
  // When you have two commits r1 and r2 you can ask for commits that are
  // reachable from r2 excluding those that are reachable from r1 by ^r1 r2
  // and it can be written as
  //   r1..r2.
  private def dotdot(r1: Branch, r2: Branch): String =
    s"${r1.name}..${r2.name}"
}
