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

import better.files.File
import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.application.Config
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.{BracketThrowable, Nel}

final class FileGitAlg[F[_]](implicit
    config: Config,
    fileAlg: FileAlg[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: BracketThrowable[F]
) extends GenGitAlg[F, File] {
  override def branchAuthors(repo: File, branch: Branch, base: Branch): F[List[String]] =
    exec(Nel.of("log", "--pretty=format:'%an'", dotdot(base, branch)), repo)

  override def checkoutBranch(repo: File, branch: Branch): F[Unit] =
    exec(Nel.of("checkout", branch.name), repo).void

  override def clone(repo: File, url: Uri): F[Unit] =
    workspaceAlg.rootDir.flatMap { rootDir =>
      exec(Nel.of("clone", "--recursive", url.toString, repo.pathAsString), rootDir).void
    }

  override def cloneExists(repo: File): F[Boolean] =
    fileAlg.isDirectory(repo / ".git")

  override def commitAll(repo: File, message: String): F[Commit] = {
    val sign = if (config.signCommits) List("--gpg-sign") else List("--no-gpg-sign")
    exec(Nel.of("commit", "--all", "-m", message) ++ sign, repo).as(Commit())
  }

  override def containsChanges(repo: File): F[Boolean] =
    exec(Nel.of("status", "--porcelain", "--untracked-files=no", "--ignore-submodules"), repo)
      .map(_.nonEmpty)

  override def createBranch(repo: File, branch: Branch): F[Unit] =
    exec(Nel.of("checkout", "-b", branch.name), repo).void

  override def currentBranch(repo: File): F[Branch] =
    exec(Nel.of("rev-parse", "--abbrev-ref", Branch.head.name), repo)
      .map(lines => Branch(lines.mkString.trim))

  override def findFilesContaining(repo: File, string: String): F[List[String]] = {
    val args = Nel.of("grep", "-I", "--fixed-strings", "--files-with-matches", string)
    exec(args, repo).handleError(_ => List.empty[String]).map(_.filter(_.nonEmpty))
  }

  override def hasConflicts(repo: File, branch: Branch, base: Branch): F[Boolean] = {
    val tryMerge = exec(Nel.of("merge", "--no-commit", "--no-ff", branch.name), repo)
    val abortMerge = exec(Nel.of("merge", "--abort"), repo).void

    returnToCurrentBranch(repo) {
      checkoutBranch(repo, base) >> F.guarantee(tryMerge)(abortMerge).attempt.map(_.isLeft)
    }
  }

  override def isMerged(repo: File, branch: Branch, base: Branch): F[Boolean] =
    exec(Nel.of("log", "--pretty=format:'%h'", dotdot(base, branch)), repo).map(_.isEmpty)

  override def latestSha1(repo: File, branch: Branch): F[Sha1] =
    exec(Nel.of("rev-parse", "--verify", branch.name), repo)
      .flatMap(lines => F.fromEither(Sha1.from(lines.mkString("").trim)))

  override def mergeTheirs(repo: File, branch: Branch): F[Option[Commit]] =
    for {
      before <- latestSha1(repo, Branch.head)
      sign = if (config.signCommits) List("--gpg-sign") else List.empty
      _ <- exec(Nel.of("merge", "--strategy-option=theirs") ++ (sign :+ branch.name), repo)
        .handleErrorWith { throwable =>
          // Resolve CONFLICT (modify/delete) by deleting unmerged files:
          for {
            unmergedFiles <- exec(Nel.of("diff", "--name-only", "--diff-filter=U"), repo)
            _ <- Nel.fromList(unmergedFiles.filter(_.nonEmpty)) match {
              case Some(files) => files.traverse(file => exec(Nel.of("rm", file), repo))
              case None        => F.raiseError(throwable)
            }
            _ <- exec(Nel.of("commit", "--all", "--no-edit") ++ sign, repo)
          } yield List.empty
        }
      after <- latestSha1(repo, Branch.head)
    } yield Option.when(before =!= after)(Commit())

  override def push(repo: File, branch: Branch): F[Unit] =
    exec(Nel.of("push", "--force", "--set-upstream", "origin", branch.name), repo).void

  override def removeClone(repo: File): F[Unit] =
    fileAlg.deleteForce(repo)

  override def setAuthor(repo: File, author: Author): F[Unit] =
    for {
      _ <- exec(Nel.of("config", "user.email", author.email), repo)
      _ <- exec(Nel.of("config", "user.name", author.name), repo)
    } yield ()

  override def syncFork(repo: File, upstreamUrl: Uri, defaultBranch: Branch): F[Unit] =
    for {
      _ <- F.unit
      remote = "upstream"
      branch = defaultBranch.name
      remoteBranch = s"$remote/$branch"
      _ <- exec(Nel.of("remote", "add", remote, upstreamUrl.toString), repo)
      _ <- exec(Nel.of("fetch", "--tags", remote, branch), repo)
      _ <- exec(Nel.of("checkout", "-B", branch, "--track", remoteBranch), repo)
      _ <- exec(Nel.of("merge", remoteBranch), repo)
      _ <- push(repo, defaultBranch)
    } yield ()

  private def exec(command: Nel[String], cwd: File): F[List[String]] =
    processAlg.exec("git" :: command, cwd, "GIT_ASKPASS" -> config.gitAskPass.pathAsString)
}
