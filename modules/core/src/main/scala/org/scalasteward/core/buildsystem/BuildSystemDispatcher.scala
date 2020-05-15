/*
 * Copyright 2018-2020 Scala Steward contributors
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

package org.scalasteward.core.buildsystem

import cats.Monad
import cats.implicits._
import org.scalasteward.core.buildsystem.maven.MavenAlg
import org.scalasteward.core.buildsystem.sbt.SbtAlg
import org.scalasteward.core.data.Scope
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

trait BuildSystemDispatcher[F[_]] extends BuildSystemAlg[F]

object BuildSystemDispatcher {
  def create[F[_]](
      implicit
      mavenAlg: MavenAlg[F],

      sbtAlg: SbtAlg[F],
      F: Monad[F]
  ): BuildSystemDispatcher[F] = {
    val allBuildSystems = List(sbtAlg, mavenAlg)
    val fallbackBuildSystem = sbtAlg

    new BuildSystemDispatcher[F] {
      override def containsBuild(repo: Repo): F[Boolean] =
        allBuildSystems.existsM(_.containsBuild(repo))

      override def getDependencies(repo: Repo): F[List[Scope.Dependencies]] =
        foundBuildSystems(repo).flatMap(_.flatTraverse(_.getDependencies(repo)))

      override def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit] =
        foundBuildSystems(repo).flatMap(_.traverse_(_.runMigrations(repo, migrations)))

      private def foundBuildSystems(repo: Repo): F[List[BuildSystemAlg[F]]] =
        allBuildSystems.filterA(_.containsBuild(repo)).map {
          case Nil  => List(fallbackBuildSystem)
          case list => list
        }
    }
  }
}
