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

import cats.implicits._
import eu.timepit.scalasteward.dependency.DependencyRepository
import eu.timepit.scalasteward.log
import eu.timepit.scalasteward.model.Update
import eu.timepit.scalasteward.sbt.{ArtificialProject, SbtAlg, SbtVersion, ScalaVersion}
import eu.timepit.scalasteward.util.MonadThrowable

class UpdateService[F[_]](
    dependencyRepository: DependencyRepository[F],
    sbtAlg: SbtAlg[F]
) {
  def checkForUpdates(implicit F: MonadThrowable[F]): F[List[Update]] =
    dependencyRepository.getDependencies.flatMap { dependencies =>
      val (libraries, plugins) = dependencies.partition(_.sbtVersion.isEmpty)
      val libProjects = libraries
        .grouped(20)
        .map { libs =>
          ArtificialProject(
            ScalaVersion("2.12.7"),
            SbtVersion("1.2.4"),
            libs.sortBy(_.formatAsModuleId),
            List.empty
          )
        }
        .toList
      val pluginProjects = plugins
        .grouped(20)
        .map { ps =>
          ArtificialProject(
            ScalaVersion("2.12.7"),
            SbtVersion("1.2.4"),
            List.empty,
            ps.sortBy(_.formatAsModuleId)
          )
        }
        .toList
      (libProjects ++ pluginProjects).flatTraverse { prj =>
        sbtAlg.getUpdates(prj).attempt.flatMap {
          case Right(updates) =>
            log.printUpdates(updates).unsafeRunSync()
            F.pure(updates)
          case Left(_) => F.pure(List.empty[Update])
        }
      }
    }
}
