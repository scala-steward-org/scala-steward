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

object steward extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val workspace = File.home / "code/scala-steward/workspace"
    for {
      _ <- prepareEnv(workspace)
      repos <- getRepos(workspace)
      _ <- repos.traverse_(stewardRepo(_, workspace))
    } yield ExitCode.Success
  }

  def prepareEnv(workspace: File): IO[Unit] =
    for {
      _ <- io.printInfo("Update global sbt plugins")
      _ <- sbt.addGlobalPlugins
      _ <- io.printInfo(s"Clean workspace $workspace")
      _ <- io.deleteForce(workspace)
      _ <- io.mkdirs(workspace)
    } yield ()

  def getRepos(workspace: File): IO[List[GithubRepo]] = {
    val url = "https://raw.githubusercontent.com/fthomas/scala-steward/master/repos.md"
    val repo = """-\s+(.+)/(.+)""".r

    io.exec(List("curl", url), workspace).map {
      _.collect {
        case repo(owner, name) => GithubRepo(owner, name)
      }
    }
  }

  def stewardRepo(repo: GithubRepo, workspace: File): IO[Unit] =
    for {
      localRepo <- cloneAndUpdate(repo, workspace)
      _ <- updateDependencies(localRepo)
      _ <- io.deleteForce(localRepo.dir)
    } yield ()

  def cloneAndUpdate(repo: GithubRepo, workspace: File): IO[LocalRepo] =
    for {
      _ <- io.printInfo(s"Clone and update ${repo.show}")
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
      defaultBranch <- git.currentBranch(repoDir)
      _ <- git.exec(List("merge", s"upstream/${defaultBranch.name}"), repoDir)
      _ <- git.push(repoDir)
    } yield LocalRepo(repo, repoDir)

  def updateDependencies(localRepo: LocalRepo): IO[Unit] =
    for {
      _ <- io.printInfo(s"Check updates for ${localRepo.upstream.show}")
      updates <- sbt.allUpdates(localRepo.dir)
      _ <- io.printInfo(
        s"Found ${updates.size} update(s):\n" + updates.map(u => s"   ${u.show}\n").mkString
      )
      _ <- updates.traverse_(updateDependency(_, localRepo))
    } yield ()

  def updateDependency(update: DependencyUpdate, localRepo: LocalRepo): IO[Unit] = {
    val repoDir = localRepo.dir
    val updateBranch = git.branchOf(update)
    io.printInfo(s"Applying ${update.show}") >>
      git.remoteBranchExists(updateBranch, repoDir).flatMap {
        case true =>
          io.printInfo(s"Branch ${updateBranch.name} already exists")
        // TODO: Update branch with latest changes
        case false =>
          io.updateDir(repoDir, update) >> git.containsChanges(repoDir).flatMap {
            case false => io.printInfo(s"I don't know how to update ${update.artifactId}")
            case true =>
              git.returnToCurrentBranch(repoDir) { baseBranch =>
                for {
                  _ <- io.printInfo(s"Create branch ${updateBranch.name}")
                  _ <- git.createBranch(updateBranch, repoDir)
                  _ <- git.commitAll(git.commitMsg(update), repoDir)
                  _ <- git.push(repoDir)
                  _ <- io.printInfo(s"Create pull request at ${localRepo.upstream.show}")
                  _ <- github
                    .createPullRequest(localRepo.upstream, update, updateBranch, baseBranch)
                } yield ()
              }
          }
      }
  }
}
