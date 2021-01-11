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
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.data.{Resolver, Scope}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}
import org.scalasteward.core.repoconfig.RepoConfigAlg

trait BuildToolDispatcher[F[_]] extends BuildToolAlg[F] {
  def runMigrationsForAllBuildRoots(repo: Repo, migrations: Nel[Migration]): F[Unit]
  def getDependenciesForAllBuildRoots(repo: Repo): F[List[Scope.Dependencies]]
}

object BuildToolDispatcher {
  def create[F[_]](implicit
      mavenAlg: MavenAlg[F],
      millAlg: MillAlg[F],
      sbtAlg: SbtAlg[F],
      scalafmtAlg: ScalafmtAlg[F],
      repoConfigAlg: RepoConfigAlg[F],
      F: Monad[F]
  ): BuildToolDispatcher[F] = {
    val allBuildTools = List(mavenAlg, millAlg, sbtAlg)
    val fallbackBuildTool = sbtAlg

    new BuildToolDispatcher[F] {

      private def buildRootsForRepo(repo: Repo) = for {
        repoConfigOpt <- repoConfigAlg.readRepoConfig(repo)
        repoConfig <- repoConfigAlg.mergeWithDefault(repoConfigOpt)
        buildRoots = repoConfig.buildRoots.map(config =>
          BuildRoot(repo, config.relativeBuildRootPath)
        )
      } yield buildRoots

      override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
        allBuildTools.existsM(_.containsBuild(buildRoot))

      override def getDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
        for {
          dependencies <- foundBuildTools(buildRoot).flatMap(
            _.flatTraverse(_.getDependencies(buildRoot))
          )
          additionalDependencies <- getAdditionalDependencies(buildRoot)
        } yield Scope.combineByResolvers(additionalDependencies ::: dependencies)

      override def runMigrations(buildRoot: BuildRoot, migrations: Nel[Migration]): F[Unit] =
        foundBuildTools(buildRoot).flatMap(_.traverse_(_.runMigrations(buildRoot, migrations)))

      private def foundBuildTools(buildRoot: BuildRoot): F[List[BuildToolAlg[F]]] =
        allBuildTools.filterA(_.containsBuild(buildRoot)).map {
          case Nil  => List(fallbackBuildTool)
          case list => list
        }

      def getAdditionalDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
        scalafmtAlg
          .getScalafmtDependency(buildRoot)
          .map(_.map(dep => Scope(List(dep), List(Resolver.mavenCentral))).toList)

      override def runMigrationsForAllBuildRoots(
          repo: Repo,
          migrations: org.scalasteward.core.util.Nel[Migration]
      ): F[Unit] = for {
        buildRoots <- buildRootsForRepo(repo)
        _ <- buildRoots.traverse(runMigrations(_, migrations))
      } yield ()

      override def getDependenciesForAllBuildRoots(repo: Repo): F[List[Scope.Dependencies]] = for {
        buildRoots <- buildRootsForRepo(repo)
        deps <- buildRoots.flatTraverse(getDependencies)
      } yield deps
    }
  }
}
