/*
 * Copyright 2018-2023 Scala Steward contributors
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
import cats.effect.MonadCancelThrow
import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.application.Config.GitCfg
import org.scalasteward.core.git.FileGitAlg.{dotdot, gitCmd}
import org.scalasteward.core.io.process.{ProcessFailedException, SlurpOptions}
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel

final class FileGitAlg[F[_]](config: GitCfg)(implicit
    fileAlg: FileAlg[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: MonadCancelThrow[F]
) extends GenGitAlg[F, File] {
  override def add(repo: File, file: String): F[Unit] =
    git_("add", file)(repo).void

  override def branchAuthors(repo: File, branch: Branch, base: Branch): F[List[String]] =
    git("log", "--pretty=format:'%an'", dotdot(base, branch))(repo).map(_.distinct)

  override def branchExists(repo: File, branch: Branch): F[Boolean] =
    git_("branch", "--list", "--no-color", "--all", branch.name)(repo).map(_.mkString.trim.nonEmpty)

  override def branchesDiffer(repo: File, b1: Branch, b2: Branch): F[Boolean] =
    git_("diff", "--name-only", b1.name, b2.name, "--")(repo).map(_.nonEmpty)

  override def checkoutBranch(repo: File, branch: Branch): F[Unit] =
    git_("checkout", branch.name)(repo).void

  override def checkIgnore(repo: File, file: String): F[Boolean] =
    git_("check-ignore", file)(repo)
      .as(true)
      .recover { case ex: ProcessFailedException if ex.exitValue === 1 => false }

  override def clone(repo: File, url: Uri): F[Unit] =
    for {
      rootDir <- workspaceAlg.rootDir
      _ <- git_("clone", "-c", "clone.defaultRemoteName=origin", url.toString, repo.pathAsString)(
        rootDir
      )
    } yield ()

  override def cloneExists(repo: File): F[Boolean] =
    fileAlg.isDirectory(repo / ".git")

  override def commitAll(repo: File, message: CommitMsg): F[Commit] = {
    val messages = message.paragraphs.foldMap(m => List("-m", m))
    val trailers = message.trailers.foldMap { case (k, v) => List("--trailer", s"$k=$v") }
    git_("commit" :: "--all" :: sign :: messages ++ trailers: _*)(repo) >>
      latestSha1(repo, Branch.head).map(Commit.apply)
  }

  override def containsChanges(repo: File): F[Boolean] =
    git_("status", "--porcelain", "--untracked-files=no", "--ignore-submodules")(repo)
      .map(_.nonEmpty)

  override def createBranch(repo: File, branch: Branch): F[Unit] =
    git_("checkout", "-b", branch.name)(repo).void

  override def currentBranch(repo: File): F[Branch] =
    git("rev-parse", "--abbrev-ref", Branch.head.name)(repo)
      .map(lines => Branch(lines.mkString.trim))

  override def deleteLocalBranch(repo: File, branch: Branch): F[Unit] =
    git_("branch", "--delete", "--force", branch.name)(repo).void

  override def deleteRemoteBranch(repo: File, branch: Branch): F[Unit] =
    git_("push", "origin", "--delete", branch.name)(repo).void

  override def discardChanges(repo: File): F[Unit] =
    git_("checkout", "--", ".")(repo).void

  override def findFilesContaining(repo: File, string: String): F[List[String]] =
    git("grep", "-I", "--fixed-strings", "--files-with-matches", string)(repo)
      .handleError(_ => List.empty[String])
      .map(_.filter(_.nonEmpty))

  override def hasConflicts(repo: File, branch: Branch, base: Branch): F[Boolean] = {
    val tryMerge = git_("merge", "--no-commit", "--no-ff", branch.name)(repo)
    val abortMerge = git_("merge", "--abort")(repo).attempt.void

    returnToCurrentBranch(repo) {
      checkoutBranch(repo, base) >> F.guarantee(tryMerge, abortMerge).attempt.map(_.isLeft)
    }
  }

  override def initSubmodules(repo: File): F[Unit] =
    git_("submodule", "update", "--init", "--recursive")(repo).void

  override def isMerged(repo: File, branch: Branch, base: Branch): F[Boolean] =
    git_("log", "--pretty=format:%h", dotdot(base, branch))(repo).map(_.isEmpty)

  override def latestSha1(repo: File, branch: Branch): F[Sha1] =
    git("rev-parse", "--verify", branch.name)(repo)
      .flatMap(lines => F.fromEither(Sha1.from(lines.mkString("").trim)))

  override def push(repo: File, branch: Branch): F[Unit] =
    git_("push", "--force", "--set-upstream", "origin", branch.name)(repo).void

  override def removeClone(repo: File): F[Unit] =
    fileAlg.deleteForce(repo)

  override def resetHard(repo: File, base: Branch): F[Unit] =
    git_("reset", "--hard", base.name)(repo).void

  override def setAuthor(repo: File, author: Author): F[Unit] =
    for {
      _ <- git_("config", "user.email", author.email)(repo)
      _ <- git_("config", "user.name", author.name)(repo)
      _ <- author.signingKey.traverse_(key => git_("config", "user.signingKey", key)(repo))
    } yield ()

  override def syncFork(repo: File, upstreamUrl: Uri, defaultBranch: Branch): F[Unit] =
    for {
      _ <- F.unit
      remote = "upstream"
      branch = defaultBranch.name
      remoteBranch = s"$remote/$branch"
      _ <- git_("remote", "add", remote, upstreamUrl.toString)(repo)
      _ <- git_("fetch", "--force", "--tags", remote, branch)(repo)
      _ <- git_("checkout", "-B", branch, "--track", remoteBranch)(repo)
      _ <- git_("merge", remoteBranch)(repo)
      _ <- push(repo, defaultBranch)
    } yield ()

  override def version: F[String] =
    workspaceAlg.rootDir.flatMap(git("--version")(_)).map(_.mkString.trim)

  private def git(args: String*)(
      repo: File,
      slurpOptions: SlurpOptions = Set.empty
  ): F[List[String]] = {
    val extraEnv = List("GIT_ASKPASS" -> config.gitAskPass.pathAsString)
    processAlg.exec(gitCmd ++ args.toList, repo, extraEnv, slurpOptions)
  }

  private def git_(args: String*)(repo: File): F[List[String]] =
    git(args: _*)(repo, SlurpOptions.ignoreBufferOverflow)

  private val sign: String =
    if (config.signCommits) "--gpg-sign" else "--no-gpg-sign"
}

object FileGitAlg {
  val gitCmd: Nel[String] = Nel.of("git", "-c", "core.hooksPath=/dev/null")

  // man 7 gitrevisions:
  // When you have two commits r1 and r2 you can ask for commits that are
  // reachable from r2 excluding those that are reachable from r1 by ^r1 r2
  // and it can be written as
  //   r1..r2.
  private def dotdot(r1: Branch, r2: Branch): String =
    s"${r1.name}..${r2.name}"
}
