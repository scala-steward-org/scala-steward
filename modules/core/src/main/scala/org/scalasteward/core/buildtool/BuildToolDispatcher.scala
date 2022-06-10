/*
 * Copyright 2018-2022 Scala Steward contributors
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
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.data.Scope
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}
import org.scalasteward.core.buildtool.gradle.GradleAlg

final class BuildToolDispatcher[F[_]](implicit
    mavenAlg: MavenAlg[F],
    gradleAlg: GradleAlg[F],
    millAlg: MillAlg[F],
    sbtAlg: SbtAlg[F],
    scalafmtAlg: ScalafmtAlg[F],
    F: Monad[F]
) {
  def getDependencies(repo: Repo, repoConfig: RepoConfig): F[List[Scope.Dependencies]] =
    getBuildRootsAndTools(repo, repoConfig).flatMap(_.flatTraverse { case (buildRoot, buildTools) =>
      for {
        dependencies <- buildTools.flatTraverse(_.getDependencies(buildRoot))
        maybeScalafmtDependency <- scalafmtAlg.getScopedScalafmtDependency(buildRoot)
      } yield Scope.combineByResolvers(maybeScalafmtDependency.toList ::: dependencies)
    })

  def runMigration(repo: Repo, repoConfig: RepoConfig, migration: ScalafixMigration): F[Unit] =
    getBuildRootsAndTools(repo, repoConfig).flatMap(_.traverse_ { case (buildRoot, buildTools) =>
      buildTools.traverse_(_.runMigration(buildRoot, migration))
    })

  private def getBuildRoots(repo: Repo, repoConfig: RepoConfig): List[BuildRoot] =
    repoConfig.buildRootsOrDefault.map(buildRootCfg => BuildRoot(repo, buildRootCfg.relativePath))

  private val allBuildTools = List(mavenAlg, gradleAlg, millAlg, sbtAlg)
  private val fallbackBuildTool = List(sbtAlg)

  private def findBuildTools(buildRoot: BuildRoot): F[(BuildRoot, List[BuildToolAlg[F]])] =
    allBuildTools.filterA(_.containsBuild(buildRoot)).map {
      case Nil  => buildRoot -> fallbackBuildTool
      case list => buildRoot -> list
    }

  private def getBuildRootsAndTools(
      repo: Repo,
      repoConfig: RepoConfig
  ): F[List[(BuildRoot, List[BuildToolAlg[F]])]] =
    getBuildRoots(repo, repoConfig).traverse(findBuildTools)
}
