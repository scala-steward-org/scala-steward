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
import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object steward extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val workspace = File.home / "code/scala-steward/workspace"
    val repos = List(GithubRepo("fthomas", "datapackage"))

    for {
      _ <- prepareEnv(workspace)
      _ <- repos.traverse_(stewardRepo(_, workspace))
    } yield ExitCode.Success
  }

  def prepareEnv(workspace: File): IO[Unit] =
    for {
      _ <- io.printInfo("Adding global sbt plugins.")
      _ <- sbt.addGlobalPlugins
      _ <- io.printInfo(s"Cleaning workspace $workspace.")
      _ <- io.deleteForce(workspace)
      _ <- io.mkdirs(workspace)
    } yield ()

  def stewardRepo(repo: GithubRepo, workspace: File): IO[Unit] =
    for {
      _ <- io.printInfo(s"Steward $repo.")
      _ <- github.fork(repo)
      repoDir = workspace / repo.owner / repo.repo
      _ <- io.mkdirs(repoDir)
      forkedRepo = GithubRepo(github.myLogin, repo.repo)
      _ <- git.clone(forkedRepo.gitUrl, repoDir, workspace)
      _ <- updateDependencies(repoDir, repo)
    } yield ()

  def updateDependencies(repoDir: File, repo: GithubRepo): IO[Unit] =
    for {
      _ <- io.printInfo(s"Check dependency updates for $repo")
      //updates <- sbt.allUpdates(repoDir)
      updates = List(
        DependencyUpdate("org.scala-js", "sbt-scalajs", "0.6.11", NonEmptyList.of("0.6.25"))
      )
      _ <- io.printInfo(
        s"Found ${updates.size} update(s):\n" + updates.map(u => s"   $u\n").mkString
      )
      _ <- updates.traverse_(updateDependency(_, repoDir))
    } yield ()

  def updateDependency(update: DependencyUpdate, repoDir: File): IO[Unit] = {
    val updateBranch = git.branchName(update)
    io.printInfo(s"Appying $update") >>
      git.remoteBranchExists(updateBranch, repoDir).flatMap {
        case true => io.printInfo(s"Branch $updateBranch already exists.")
        case false =>
          io.updateDir(repoDir, update) >> git.containsChanges(repoDir).flatMap {
            case false => io.printInfo(s"I don't know how to update ${update.artifactId}")
            case true =>
              git.returnToCurrentBranch(repoDir) { _ =>
                for {
                  _ <- git.createBranch(updateBranch, repoDir)
                  _ <- git.commitAll(git.commitMsg(update), repoDir)
                  _ <- git.exec(List("push"), repoDir)
                } yield ()
              }
          }
      }
  }
}
