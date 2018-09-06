/*
 * Copyright 2018 scruffy contributors
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

package eu.timepit.scruffy

import better.files.File
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import java.nio.file.Paths

object Main {
  def main(args: Array[String]): Unit = {
    val repos = List("fthomas/refined")
    val workspace = File(Paths.get("/home/frank/code/scruffy/workspace"))

    prepareEnv(workspace).unsafeRunSync()
    cloneRepos(repos, workspace)
    ()
  }

  def prepareEnv(workspace: File): IO[Unit] =
    for {
      _ <- io.printLnInfo("Add global sbt plugins.")
      _ <- sbt.addGlobalPlugins
      _ <- io.printLnInfo(s"Clean workspace $workspace.")
      _ <- io.delete(workspace)
      _ <- io.mkdirs(workspace)
    } yield ()

  def cloneRepos(repos: List[String], workspace: File): Unit =
    repos.foreach { repo =>
      val repoDir = workspace / repo
      cloneRepo(repo, repoDir, workspace)
    }

  def cloneRepo(repo: String, repoDir: File, workspace: File): Unit = {
    val url = "https://github.com/" + repo
    git.clone(workspace, url, repoDir).unsafeRunSync()

    val p = io.delete(repoDir) >> io.mkdirs(repoDir)
    p.unsafeRunSync()

    // can we set up sbt plugins in workspace ?
    //sbt.dependencyUpdates(repoDir).map(println).unsafeRunSync()
    val update =
      DependencyUpdate("com.github.pureconfig", "pureconfig", "0.9.1", NonEmptyList.of("0.9.2"))
    val repo1 = repoDir

    val p2 = for {
      _ <- git.exec(repo1, List("checkout", "-f"))
      branch <- git.currentBranch(repo1)
      _ <- git.createBranch(repo1, git.branchName(update))
      _ <- git.checkoutBranch(repo1, git.branchName(update))
      _ <- io.updateDir(repo1, update)
      _ <- git.commitAll(repo1, git.commitMsg(update))
    } yield ()
    p2.unsafeRunSync()
  }

}
