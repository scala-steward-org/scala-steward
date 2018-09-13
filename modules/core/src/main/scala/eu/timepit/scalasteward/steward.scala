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
import eu.timepit.scalasteward.model._

object steward extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val workspace = File.home / "code/scala-steward/workspace"
    for {
      _ <- prepareEnv(workspace)
      repos <- getRepos(workspace)
      _ <- repos.traverse_ { repo =>
        log.printTimed(duration => s"Updating ${repo.show} took $duration\n")(
          stewardRepo(repo, workspace)
        )
      }
    } yield ExitCode.Success
  }

  def prepareEnv(workspace: File): IO[Unit] =
    for {
      _ <- log.printInfo("Update global sbt plugins")
      _ <- sbt.addGlobalPlugins
      _ <- log.printInfo(s"Clean workspace $workspace")
      _ <- io.deleteForce(workspace)
      _ <- io.mkdirs(workspace)
    } yield ()

  def getRepos(workspace: File): IO[List[GithubRepo]] =
    IO {
      val file = workspace / ".." / "repos.md"
      val regex = """-\s+(.+)/(.+)""".r
      file.lines.collect { case regex(owner, repo) => GithubRepo(owner, repo) }.toList
    }

  def stewardRepo(repo: GithubRepo, workspace: File): IO[Unit] = {
    val p = for {
      localRepo <- cloneAndUpdate(repo, workspace)
      _ <- updateDependencies(localRepo)
      _ <- io.deleteForce(localRepo.dir)
    } yield ()
    p.attempt.flatMap {
      case Right(_) => IO.unit
      case Left(t)  => IO(println(t))
    }
  }

  def cloneAndUpdate(repo: GithubRepo, workspace: File): IO[LocalRepo] =
    for {
      _ <- log.printInfo(s"Clone and update ${repo.show}")
      _ <- github.fork(repo) // This is a NOP if the fork already exists.
      repoDir = workspace / repo.owner / repo.repo
      _ <- io.mkdirs(repoDir)
      // TODO: Which of my repos is the fork of $repo? $repo.repo is not reliable.
      forkName = repo.repo
      forkRepo = GithubRepo(github.myLogin, forkName)
      forkUrl <- github.httpsUrlWithCredentials(forkRepo)
      _ <- git.clone(forkUrl, repoDir, workspace)
      _ <- git.setUserSteward(repoDir)
      _ <- github.fetchUpstream(repo, repoDir)
      // TODO: Determine the current default branch
      baseBranch <- git.currentBranch(repoDir)
      _ <- git.exec(List("merge", s"upstream/${baseBranch.name}"), repoDir)
      _ <- git.push(baseBranch, repoDir)
    } yield LocalRepo(repo, repoDir, baseBranch)

  def updateDependencies(localRepo: LocalRepo): IO[Unit] =
    for {
      _ <- log.printInfo(s"Check updates for ${localRepo.upstream.show}")
      // TODO: Run this in a sandbox
      updates <- sbt.allUpdates(localRepo.dir)
      _ <- log.printUpdates(updates)
      _ <- updates.traverse_(update => updateDependency(LocalUpdate(localRepo, update)))
    } yield ()

  def updateDependency(localUpdate: LocalUpdate): IO[Unit] = {
    val repoDir = localUpdate.localRepo.dir
    val update = localUpdate.update
    val updateBranch = localUpdate.updateBranch
    val localRepo = localUpdate.localRepo

    log.printInfo(s"Applying ${update.show}") >>
      git.remoteBranchExists(updateBranch, repoDir).flatMap {
        case true => resetAndUpdate(update, updateBranch, localRepo)
        case false =>
          io.updateDir(repoDir, update) >> git.containsChanges(repoDir).flatMap {
            case false => log.printWarning(s"I don't know how to update ${update.name}")
            case true =>
              git.returnToCurrentBranch(repoDir) { baseBranch =>
                for {
                  _ <- log.printInfo(s"Create branch ${updateBranch.name}")
                  _ <- git.createBranch(updateBranch, repoDir)
                  _ <- git.commitAll(git.commitMsg(update), repoDir)
                  _ <- git.push(updateBranch, repoDir)
                  _ <- log.printInfo(s"Create pull request at ${localRepo.upstream.show}")
                  _ <- github.createPullRequest(localUpdate)
                } yield ()
              }
          }
      }
  }

  def resetAndUpdate(
      update: Update,
      updateBranch: Branch,
      localRepo: LocalRepo
  ): IO[Unit] =
    for {
      _ <- git.checkoutBranch(updateBranch, localRepo.dir)
      isMerged <- git.isMerged(updateBranch, localRepo.dir)
      isBehind <- git.isBehind(updateBranch, localRepo.base, localRepo.dir)
      _ <- if (!isMerged && isBehind)
        for {
          _ <- log.printInfo(s"Reset and update branch ${updateBranch.name}")
          _ <- git.exec(List("reset", "--hard", localRepo.base.name), localRepo.dir)
          _ <- io.updateDir(localRepo.dir, update)
          _ <- git.commitAll(git.commitMsg(update), localRepo.dir)
          _ <- git.push(updateBranch, localRepo.dir)
        } yield ()
      else IO.unit
      // TODO: Create PR if it not exists
    } yield ()
}
