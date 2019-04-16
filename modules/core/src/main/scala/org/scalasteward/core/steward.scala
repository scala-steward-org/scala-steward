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

package org.scalasteward.core

import better.files.File
import cats.FlatMap
import cats.effect.{ExitCode, IO, IOApp, Sync}
import cats.implicits._
import org.scalasteward.core.application.Context
import org.scalasteward.core.vcs.data.Repo

object steward extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Context.create[IO](args).use { ctx =>
      ctx.logAlg.infoTotalTime("run") {
        for {
          repos <- readRepos[IO](ctx.config.reposFile)
          _ <- prepareEnv(ctx)
          _ <- repos.traverse(ctx.dependencyService.checkDependencies)
          allUpdates <- ctx.updateService.checkForUpdates(repos)
          reposToNurture <- ctx.updateService.filterByApplicableUpdates(repos, allUpdates)
          _ <- IO(reposToNurture.map(_.show).foreach(println))
          _ <- IO(println(reposToNurture.size))
          _ <- reposToNurture.filter(repos.contains).traverse_(ctx.nurtureAlg.nurture)
          //_ <- repos.traverse_(ctx.nurtureAlg.nurture)
        } yield ExitCode.Success
      }
    }

  def prepareEnv[F[_]: FlatMap](ctx: Context[F]): F[Unit] =
    for {
      _ <- ctx.sbtAlg.addGlobalPlugins
      _ <- ctx.workspaceAlg.cleanWorkspace
    } yield ()

  def readRepos[F[_]](reposFile: File)(implicit F: Sync[F]): F[List[Repo]] =
    F.delay {
      val regex = """-\s+(.+)/(.+)""".r
      reposFile.lines.collect { case regex(owner, repo) => Repo(owner.trim, repo.trim) }.toList
    }
}
