/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.application

import better.files.File
import cats.Monad
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import org.scalasteward.core.dependency.DependencyService
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.NurtureAlg
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.update.UpdateService
import org.scalasteward.core.util.LogAlg
import org.scalasteward.core.vcs.data.Repo

final class StewardAlg[F[_]](
    implicit
    config: Config,
    dependencyService: DependencyService[F],
    fileAlg: FileAlg[F],
    logAlg: LogAlg[F],
    nurtureAlg: NurtureAlg[F],
    sbtAlg: SbtAlg[F],
    updateService: UpdateService[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  def prepareEnv: F[Unit] =
    for {
      _ <- sbtAlg.addGlobalPlugins
      _ <- workspaceAlg.cleanWorkspace
    } yield ()

  def readRepos(reposFile: File): F[List[Repo]] =
    fileAlg.readFile(reposFile).map { maybeContent =>
      val regex = """-\s+(.+)/(.+)""".r
      val content = maybeContent.getOrElse("")
      content.linesIterator.collect { case regex(owner, repo) => Repo(owner.trim, repo.trim) }.toList
    }

  def pruneRepos(repos: List[Repo]): F[List[Repo]] =
    logAlg.infoTotalTime("pruning repos") {
      for {
        _ <- repos.traverse(dependencyService.checkDependencies)
        allUpdates <- updateService.checkForUpdates(repos)
        reposToNurture <- updateService.filterByApplicableUpdates(repos, allUpdates)
        _ = println(reposToNurture.size)
      } yield reposToNurture
    }

  def runF: F[ExitCode] =
    logAlg.infoTotalTime("run") {
      for {
        _ <- prepareEnv
        repos <- readRepos(config.reposFile)
        prunedRepos <- pruneRepos(repos)
        //prunedRepos = repos
        _ <- prunedRepos.traverse_(nurtureAlg.nurture)
      } yield ExitCode.Success
    }
}

object StewardAlg extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Context.create[IO](args).use(_.runF)
}
