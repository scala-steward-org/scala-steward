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
import eu.timepit.scalasteward.dependency.Dependency
import eu.timepit.scalasteward.dependency.parser.parseDependencies
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import eu.timepit.scalasteward.model.Update
import eu.timepit.scalasteward.sbtLegacy

trait SbtAlg[F[_]] {
  def getDependencies(repo: Repo): F[List[Dependency]]

  def getUpdates(project: ArtificialProject): F[List[Update]]
}

object SbtAlg {
  val sbtCmd: List[String] =
    List("sbt", "-no-colors")

  def sync[F[_]](
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F]
  )(implicit F: Sync[F]): SbtAlg[F] =
    new SbtAlg[F] {
      override def getDependencies(repo: Repo): F[List[Dependency]] =
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          jvmopts = repoDir / ".jvmopts"
          cmd = ";libraryDependenciesAsJson ;reload plugins; libraryDependenciesAsJson"
          lines <- fileAlg.removeTemporarily(jvmopts) {
            processAlg.execSandboxed(sbtCmd :+ cmd, repoDir)
          }
        } yield lines.flatMap(parseDependencies)

      override def getUpdates(project: ArtificialProject): F[List[Update]] =
        for {
          updatesDir <- workspaceAlg.rootDir.map(_ / "updates")
          projectDir = updatesDir / "project"
          _ <- fileAlg.writeFileData(updatesDir, project.mkBuildSbt)
          _ <- fileAlg.writeFileData(projectDir, project.mkBuildProperties)
          _ <- fileAlg.writeFileData(projectDir, project.mkPluginsSbt)
          lines <- processAlg.exec(sbtCmd :+ project.dependencyUpdatesCmd, updatesDir)
          _ <- fileAlg.deleteForce(updatesDir)
        } yield sbtLegacy.sanitizeUpdates(sbtLegacy.toUpdates(lines))
    }
}
