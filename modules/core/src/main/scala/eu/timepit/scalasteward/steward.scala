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
import eu.timepit.scalasteward.model._
import eu.timepit.scalasteward.util._

object steward extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    log.printTotalTime {
      Context.create[IO].use { ctx =>
        for {
          repos <- getRepos(ctx.config.workspace)
          _ <- prepareEnv(ctx)
          //user <- ctx.config.gitHubUser[IO]
          //_ <- repos.traverse(ctx.dependencyService.forkAndCheckDependencies(user, _))
          //_ <- ctx.updateService.checkForUpdates
          _ <- repos.traverse_(stewardRepo(_, ctx))
        } yield ExitCode.Success
      }
    }

  def prepareEnv(ctx: Context[IO]): IO[Unit] =
    for {
      _ <- ctx.sbtAlg.addGlobalPlugins
      _ <- ctx.workspaceAlg.cleanWorkspace
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
      repoOut <- ctx.gitHubApiAlg.createFork(repo)
      repoDir <- ctx.workspaceAlg.repoDir(repo)
      forkUrl = util.uri.withUserInfo(repoOut.clone_url, user)
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
      _ <- ctx.logger.info(util.logger.showUpdates(updates))
      filteredUpdates <- ctx.filterAlg.filterMany(localRepo.upstream, updates)
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
      gitLegacy.checkoutBranch(updateBranch, dir) >> ifTrue(
        ctx.nurtureAlg
          .shouldBeReset(localUpdate.localRepo.upstream, updateBranch, localUpdate.localRepo.base)
      ) {
        for {
          _ <- log.printInfo(s"Reset and update branch ${updateBranch.name}")
          _ <- ctx.gitAlg.resetHard(localUpdate.localRepo.upstream, localUpdate.localRepo.base)
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
      _ <- ctx.gitAlg.push(localUpdate.localRepo.upstream, localUpdate.updateBranch)
      _ <- githubLegacy.createPullRequestIfNotExists(localUpdate, ctx.gitHubApiAlg, ctx.config)
    } yield ()
  }
}
