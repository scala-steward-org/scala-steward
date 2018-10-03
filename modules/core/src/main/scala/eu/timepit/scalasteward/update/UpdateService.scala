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

package eu.timepit.scalasteward.update

import cats.Monad
import cats.implicits._
import eu.timepit.scalasteward.dependency.DependencyRepository
import eu.timepit.scalasteward.model.Update
import eu.timepit.scalasteward.sbt.{ArtificialProject, SbtAlg, SbtVersion, ScalaVersion}

class UpdateService[F[_]](
    dependencyRepository: DependencyRepository[F],
    sbtAlg: SbtAlg[F]
) {
  def checkForUpdates(implicit F: Monad[F]): F[List[Update]] =
    dependencyRepository.getDependencies.flatMap { dependencies =>
      val (libraries, plugins) = dependencies.partition(_.sbtVersion.isEmpty)
      val project =
        ArtificialProject(
          ScalaVersion("2.12.7"),
          SbtVersion("1.2.3"),
          libraries.sortBy(_.formatAsModuleId),
          plugins.sortBy(_.formatAsModuleId)
        )
      sbtAlg.getUpdates(project)
    }
}
