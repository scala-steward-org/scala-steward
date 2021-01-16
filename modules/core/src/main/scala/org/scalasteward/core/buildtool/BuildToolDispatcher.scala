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
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.vcs.data.BuildRoot

trait BuildToolDispatcher[F[_]] extends BuildToolAlg[F, Repo]

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

      private def buildRootsForRepo(repo: Repo): F[List[BuildRoot]] = for {
        repoConfigOpt <- repoConfigAlg.readRepoConfig(repo)
        repoConfig <- repoConfigAlg.mergeWithDefault(repoConfigOpt)
        buildRoots = repoConfig.buildRootsOrDefault
          .map(config => BuildRoot(repo, config.relativePath))
      } yield buildRoots

      override def containsBuild(repo: Repo): F[Boolean] =
        buildRootsForRepo(repo).flatMap(buildRoots =>
          buildRoots.existsM(buildRoot => allBuildTools.existsM(_.containsBuild(buildRoot)))
        )

      override def getDependencies(repo: Repo): F[List[Scope.Dependencies]] =
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

      override def runMigration(repo: Repo, migration: Migration): F[Unit] =
        buildRootsForRepo(repo).flatMap(buildRoots =>
          buildRoots.traverse_(buildRoot =>
            foundBuildTools(buildRoot).flatMap(_.traverse_(_.runMigration(buildRoot, migration)))
          )
        )

      private def foundBuildTools(buildRoot: BuildRoot): F[List[BuildToolAlg[F, BuildRoot]]] =
        allBuildTools.filterA(_.containsBuild(buildRoot)).map {
          case Nil  => List(fallbackBuildTool)
          case list => list
        }

      def getAdditionalDependencies(buildRoot: BuildRoot): F[List[Scope.Dependencies]] =
        scalafmtAlg
          .getScalafmtDependency(buildRoot)
          .map(_.map(dep => Scope(List(dep), List(Resolver.mavenCentral))).toList)
    }
  }
}
