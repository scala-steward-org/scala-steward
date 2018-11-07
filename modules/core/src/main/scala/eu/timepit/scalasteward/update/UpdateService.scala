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
import eu.timepit.scalasteward.dependency.{Dependency, DependencyRepository}
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.model.Update
import eu.timepit.scalasteward.sbt._
import eu.timepit.scalasteward.util
import eu.timepit.scalasteward.util.MonadThrowable
import io.chrisdavenport.log4cats.Logger

class UpdateService[F[_]](
    implicit
    dependencyRepository: DependencyRepository[F],
    filterAlg: FilterAlg[F],
    logger: Logger[F],
    sbtAlg: SbtAlg[F],
    updateRepository: UpdateRepository[F],
    F: Monad[F]
) {

  // Add configuration "phantom-js-jetty" to Update

  // WIP
  def checkForUpdates(repos: List[Repo])(implicit F: MonadThrowable[F]): F[List[Update]] =
    dependencyRepository.getDependencies(repos).flatMap { dependencies =>
      val (libraries, plugins) = dependencies
        .filter(
          d =>
            d.groupId != "org.scala-lang" && d.artifactId != "scala-library"
              && d.groupId != "org.eclipse.jetty" && d.artifactId != "jetty-server" &&
              d.artifactId != "jetty-websocket"
        )
        .partition(_.sbtVersion.isEmpty)
      val libProjects = splitter
        .xxx(libraries)
        .map { libs =>
          ArtificialProject(
            ScalaVersion("2.12.7"),
            defaultSbtVersion,
            libs.sortBy(_.formatAsModuleId),
            List.empty
          )
        }

      val pluginProjects = plugins
        .groupBy(_.sbtVersion)
        .flatMap {
          case (maybeSbtVersion, plugins) =>
            splitter.xxx(plugins).map { ps =>
              ArtificialProject(
                ScalaVersion("2.12.7"),
                seriesToSpecificVersion(maybeSbtVersion.get),
                List.empty,
                ps.sortBy(_.formatAsModuleId)
              )
            }
        }
        .toList

      /*
      val pluginProjects = splitter
        .xxx(plugins)
        .map { ps =>
          ArtificialProject(
            ScalaVersion("2.12.7"),
            SbtVersion("1.2.4"),
            List.empty,
            ps.sortBy(_.formatAsModuleId)
          )
        }*/
      val x = (libProjects ++ pluginProjects).flatTraverse { prj =>
        val fa = util.divideOnError((_: ArtificialProject).halve)(sbtAlg.getUpdates)(prj)
        //val fa = sbtAlg.getUpdates(prj)

        fa.attempt.flatMap {
          case Right(updates) =>
            logger.info(util.logger.showUpdates(updates)) >>
              updates.traverse_(updateRepository.save) >> F.pure(updates)
          case Left(t) =>
            println(t)
            F.pure(List.empty[Update])
        }
      }

      x.flatMap(updates => filterAlg.globalFilterMany(updates))
    }

  def foo(updates: List[Update]): F[List[Repo]] =
    dependencyRepository.getStore.map { store =>
      store
        .filter(_._2.dependencies.exists(d => updates.exists(u => UpdateService.isUpdateFor(u, d))))
        .keys
        .toList
        .sorted
    }
}

object UpdateService {
  def isUpdateFor(update: Update, dependency: Dependency): Boolean =
    update.groupId === dependency.groupId &&
      update.artifactId === dependency.artifactIdCross &&
      update.currentVersion === dependency.version
}
