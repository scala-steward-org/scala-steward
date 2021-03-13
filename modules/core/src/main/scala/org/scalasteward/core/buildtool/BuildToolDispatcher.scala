/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core.buildtool

import cats.Monad
import cats.syntax.all._
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.data.Scope
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

final class BuildToolDispatcher[F[_]](config: Config)(implicit
    mavenAlg: MavenAlg[F],
    millAlg: MillAlg[F],
    sbtAlg: SbtAlg[F],
    scalafmtAlg: ScalafmtAlg[F],
    repoConfigAlg: RepoConfigAlg[F],
    F: Monad[F]
) {
  private val allBuildTools = List(mavenAlg, millAlg, sbtAlg)
  private val fallbackBuildTool = sbtAlg

  private def buildRootsForRepo(repo: Repo): F[List[BuildRoot]] = for {
    repoConfigOpt <- repoConfigAlg.readRepoConfig(repo)
    repoConfig <- repoConfigAlg.mergeWithDefault(repoConfigOpt)
    buildRoots = repoConfig.buildRootsOrDefault
      .map(config => BuildRoot(repo, config.relativePath))
  } yield buildRoots

  def getDependencies(repo: Repo): F[List[Scope.Dependencies]] =
    for {
      buildRoots <- buildRootsForRepo(repo)
      result <- buildRoots.flatTraverse(buildRoot =>
        for {
          dependencies <- foundBuildTools(buildRoot).flatMap(
            _.flatTraverse(_.getDependencies(buildRoot))
          )
          additionalDependencies <- getAdditionalDependencies(buildRoot)
        } yield Scope.combineByResolvers(additionalDependencies ::: dependencies)
      )
    } yield result

  def runMigration(repo: Repo, migration: ScalafixMigration): F[Unit] =
    buildRootsForRepo(repo).flatMap(buildRoots =>
      buildRoots.traverse_(buildRoot =>
        foundBuildTools(buildRoot).flatMap(_.traverse_(_.runMigration(buildRoot, migration)))
      )
    )

  private def foundBuildTools(buildRoot: BuildRoot): F[List[BuildToolAlg[F]]] =
    allBuildTools.filterA(_.containsBuild(buildRoot)).map {
      case Nil  => List(fallbackBuildTool)
      case list => list
    }

  private def getAdditionalDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
    scalafmtAlg
      .getScalafmtDependency(buildRoot)
      .map(_.map(dep => Scope(List(dep), List(config.defaultResolver))).toList)
}
