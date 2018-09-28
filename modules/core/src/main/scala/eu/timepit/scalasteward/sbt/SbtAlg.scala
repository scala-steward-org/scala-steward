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

package eu.timepit.scalasteward.sbt

import cats.effect.Sync
import cats.implicits._
import eu.timepit.scalasteward.application.WorkspaceAlg
import eu.timepit.scalasteward.dependency.Dependency
import eu.timepit.scalasteward.dependency.parser.parseDependencies
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.ProcessAlg

trait SbtAlg[F[_]] {
  def getDependencies(repo: Repo): F[List[Dependency]]
}

object SbtAlg {
  val sbtCmd: List[String] =
    List("sbt", "-no-colors")

  def sync[F[_]](
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F]
  )(implicit F: Sync[F]): SbtAlg[F] =
    new SbtAlg[F] {
      override def getDependencies(repo: Repo): F[List[Dependency]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          cmd = ";libraryDependenciesAsJson ;reload plugins; libraryDependenciesAsJson"
          lines <- processAlg.execSandboxed(sbtCmd :+ cmd, repoDir)
        } yield lines.flatMap(parseDependencies)
    }
}
