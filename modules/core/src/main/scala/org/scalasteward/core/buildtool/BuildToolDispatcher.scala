/*
 * Copyright 2018-2025 Scala Steward contributors
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
import cats.syntax.all.*
import org.scalasteward.core.buildtool.gradle.GradleAlg
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.buildtool.scalacli.ScalaCliAlg
import org.scalasteward.core.data.{Repo, Scope}
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.typelevel.log4cats.Logger

final class BuildToolDispatcher[F[_]](implicit
    gradleAlg: GradleAlg[F],
    logger: Logger[F],
    mavenAlg: MavenAlg[F],
    millAlg: MillAlg[F],
    sbtAlg: SbtAlg[F],
    scalaCliAlg: ScalaCliAlg[F],
    scalafmtAlg: ScalafmtAlg[F],
    F: Monad[F]
) {
  def getDependencies(repo: Repo, repoConfig: RepoConfig): F[List[Scope.Dependencies]] =
    getBuildRootsAndTools(repo, repoConfig).flatMap(_.flatTraverse { case (buildRoot, buildTools) =>
      for {
        dependencies <- buildTools.flatTraverse { buildTool =>
          logger.info(s"Get dependencies in ${buildRoot.relativePath} from ${buildTool.name}") >>
            buildTool.getDependencies(buildRoot)
        }
        maybeScalafmtDependency <- scalafmtAlg.getScopedScalafmtDependency(buildRoot)
      } yield Scope.combineByResolvers(maybeScalafmtDependency.toList ::: dependencies)
    })

  def runMigration(repo: Repo, repoConfig: RepoConfig, migration: ScalafixMigration): F[Unit] =
    getBuildRootsAndTools(repo, repoConfig).flatMap(_.traverse_ { case (buildRoot, buildTools) =>
      buildTools.traverse_(_.runMigration(buildRoot, migration))
    })

  private val allBuildTools = List(gradleAlg, mavenAlg, millAlg, sbtAlg, scalaCliAlg)
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
    repoConfig.buildRootsOrDefault(repo).traverse(findBuildTools)
}
