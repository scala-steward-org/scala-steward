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

package eu.timepit.scalasteward

import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import eu.timepit.scalasteward.application.Context
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.FileAlg
import eu.timepit.scalasteward.model._
import eu.timepit.scalasteward.sbt.SbtAlg
import eu.timepit.scalasteward.util.uriUtil
import eu.timepit.scalasteward.utilLegacy._

object steward extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    log.printTotalTime {
      Context.create[IO].use { ctx =>
        for {
          repos <- getRepos(ctx.config.workspace)
          _ <- prepareEnv(ctx.config.workspace, ctx.fileAlg, ctx.sbtAlg)
          _ <- repos.traverse_(stewardRepo(_, ctx))
        } yield ExitCode.Success
      }
    }

  def prepareEnv(workspace: File, fileAlg: FileAlg[IO], sbtAlg: SbtAlg[IO]): IO[Unit] =
    for {
      _ <- sbtAlg.addGlobalPlugins
      _ <- log.printInfo(s"Clean workspace $workspace")
      _ <- fileAlg.deleteForce(workspace / "repos")
      _ <- ioLegacy.mkdirs(workspace)
    } yield ()

  def getRepos(workspace: File): IO[List[Repo]] =
    IO {
      val file = workspace / ".." / "repos.md"
      val regex = """-\s+(.+)/(.+)""".r
      file.lines.collect { case regex(owner, repo) => Repo(owner, repo) }.toList
    }

  def stewardRepo(repo: Repo, ctx: Context[IO]): IO[Unit] = {
    val p = for {
      localRepo <- cloneAndUpdate(repo, ctx)
      _ <- updateDependencies(localRepo, ctx)
      _ <- ctx.fileAlg.deleteForce(localRepo.dir)
    } yield ()
    log.printTotalTime {
      p.attempt.flatMap {
        case Right(_) => IO.unit
        case Left(t)  => IO(println(t))
      }
    }
  }

  def cloneAndUpdate(repo: Repo, ctx: Context[IO]): IO[LocalRepo] =
    for {
      _ <- log.printInfo(s"Clone and update ${repo.show}")
      user <- ctx.config.gitHubUser[IO]
      repoOut <- ctx.gitHubService.createFork(user, repo)
      repoDir <- ctx.workspaceAlg.repoDir(repo)
      forkUrl = uriUtil.withUserInfo(repoOut.clone_url, user)
      _ <- ctx.gitAlg.clone(repo, forkUrl)
      _ <- ctx.gitAlg.setAuthor(repo, ctx.config.gitAuthor)
      parent <- repoOut.parentOrRaise[IO]
      baseBranch = parent.default_branch
      _ <- ctx.gitAlg.syncFork(repo, parent.clone_url, parent.default_branch)
    } yield LocalRepo(repo, repoDir, baseBranch)

  def updateDependencies(localRepo: LocalRepo, ctx: Context[IO]): IO[Unit] =
    for {
      _ <- log.printInfo(s"Check updates for ${localRepo.upstream.show}")
      updates <- ctx.sbtAlg.getUpdatesForRepo(localRepo.upstream)
      _ <- log.printUpdates(updates)
      filteredUpdates = updates.filterNot(Update.ignore)
      _ <- filteredUpdates.traverse_(
        update => applyUpdate(LocalUpdate(localRepo, update), ctx)
      )
    } yield ()

  def applyUpdate(localUpdate: LocalUpdate, ctx: Context[IO]): IO[Unit] =
    for {
      _ <- log.printInfo(s"Apply update ${localUpdate.update.show}")
      _ <- gitLegacy
        .remoteBranchExists(localUpdate.updateBranch, localUpdate.localRepo.dir)
        .ifM(resetAndUpdate(localUpdate, ctx), applyNewUpdate(localUpdate, ctx))
    } yield ()

  def applyNewUpdate(localUpdate: LocalUpdate, ctx: Context[IO]): IO[Unit] = {
    val dir = localUpdate.localRepo.dir
    val update = localUpdate.update
    val updateBranch = localUpdate.updateBranch

    (ioLegacy.updateDir(dir, update) >> gitLegacy.containsChanges(dir)).ifM(
      gitLegacy.returnToCurrentBranch(dir) {
        for {
          _ <- log.printInfo(s"Create branch ${updateBranch.name}")
          _ <- gitLegacy.createBranch(updateBranch, dir)
          _ <- commitPushAndCreatePullRequest(localUpdate, ctx)
        } yield ()
      },
      log.printWarning(s"No files were changed")
    )
  }

  def resetAndUpdate(localUpdate: LocalUpdate, ctx: Context[IO]): IO[Unit] = {
    val dir = localUpdate.localRepo.dir
    val updateBranch = localUpdate.updateBranch

    gitLegacy.returnToCurrentBranch(dir) {
      gitLegacy.checkoutBranch(updateBranch, dir) >> ifTrue(shouldBeReset(localUpdate)) {
        for {
          _ <- log.printInfo(s"Reset and update branch ${updateBranch.name}")
          _ <- gitLegacy.exec(List("reset", "--hard", localUpdate.localRepo.base.name), dir)
          _ <- ioLegacy.updateDir(dir, localUpdate.update)
          _ <- ifTrue(gitLegacy.containsChanges(dir))(
            commitPushAndCreatePullRequest(localUpdate, ctx)
          )
        } yield ()
      }
    }
  }

  def commitPushAndCreatePullRequest(localUpdate: LocalUpdate, ctx: Context[IO]): IO[Unit] = {
    val dir = localUpdate.localRepo.dir
    for {
      _ <- gitLegacy.commitAll(localUpdate.commitMsg, dir)
      _ <- gitLegacy.push(localUpdate.updateBranch, dir)
      _ <- githubLegacy.createPullRequestIfNotExists(localUpdate, ctx.gitHubService, ctx.config)
    } yield ()
  }

  def shouldBeReset(localUpdate: LocalUpdate): IO[Boolean] = {
    val dir = localUpdate.localRepo.dir
    val baseBranch = localUpdate.localRepo.base
    val updateBranch = localUpdate.updateBranch
    for {
      isMerged <- gitLegacy.isMerged(updateBranch, baseBranch, dir)
      isBehind <- gitLegacy.isBehind(updateBranch, baseBranch, dir)
      authors <- gitLegacy.branchAuthors(updateBranch, baseBranch, dir)
      (result, msg) = {
        val pr = s"PR ${updateBranch.name}"
        if (isMerged)
          (false, s"$pr is already merged")
        else if (authors.distinct.length >= 2)
          (false, s"$pr has commits by ${authors.mkString(", ")}")
        else if (authors.length >= 2)
          (true, s"$pr has multiple commits")
        else if (isBehind)
          (true, s"$pr is behind ${baseBranch.name}")
        else
          (false, s"$pr is up-to-date with ${baseBranch.name}")
        // TODO: Only reset PRs that are still open.
      }
      _ <- log.printInfo(msg)
    } yield result
  }
}
