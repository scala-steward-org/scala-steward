/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.update

import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.dependency.{Dependency, DependencyRepository}
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.model.Update
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.sbt._
import org.scalasteward.core.sbt.data.ArtificialProject
import org.scalasteward.core.util
import org.scalasteward.core.util.MonadThrowable

class UpdateService[F[_]](
    implicit
    dependencyRepository: DependencyRepository[F],
    filterAlg: FilterAlg[F],
    logger: Logger[F],
    pullRequestRepo: PullRequestRepository[F],
    sbtAlg: SbtAlg[F],
    updateRepository: UpdateRepository[F],
    F: MonadThrowable[F]
) {

  // WIP
  def checkForUpdates(repos: List[Repo])(implicit F: MonadThrowable[F]): F[List[Update.Single]] =
    updateRepository.deleteAll >>
      dependencyRepository.getDependencies(repos).flatMap { dependencies =>
        val (libraries, plugins) = dependencies
          .filter(
            d =>
              FilterAlg.isIgnoredGlobally(d.toUpdate).isRight && UpdateService
                .includeInUpdateCheck(d)
          )
          .partition(_.sbtVersion.isEmpty)
        val libProjects = splitter
          .xxx(libraries)
          .map { libs =>
            ArtificialProject(
              defaultScalaVersion,
              defaultSbtVersion,
              libs.sortBy(_.formatAsModuleId),
              List.empty
            )
          }

        val pluginProjects = plugins
          .groupBy(_.sbtVersion)
          .flatMap {
            case (maybeSbtVersion, plugins1) =>
              splitter.xxx(plugins1).map { ps =>
                ArtificialProject(
                  defaultScalaVersion,
                  seriesToSpecificVersion(maybeSbtVersion.get),
                  List.empty,
                  ps.sortBy(_.formatAsModuleId)
                )
              }
          }
          .toList

        val x = (libProjects ++ pluginProjects).flatTraverse { prj =>
          val fa =
            util.divideOnError(prj)(sbtAlg.getUpdatesForProject)(_.halve.toList.flatMap {
              case (p1, p2) => List(p1, p2)
            }) { (failedP: ArtificialProject, t: Throwable) =>
              println(s"failed finding updates for $failedP")
              println(t)
              F.pure(List.empty[Update.Single])
            }

          fa.flatMap { updates =>
            logger.info(util.logger.showUpdates(updates.widen[Update])) >>
              updates.traverse_(updateRepository.save) >> F.pure(updates)
          }
        }

        x.flatMap(updates => filterAlg.globalFilterMany(updates))
      }

  def filterByApplicableUpdates(repos: List[Repo], updates: List[Update.Single]): F[List[Repo]] =
    repos.traverseFilter { repo =>
      for {
        dependencies <- dependencyRepository.getDependencies(List(repo))
        matchingUpdates = updates.filter { update =>
          dependencies.exists(dependency => UpdateService.isUpdateFor(update, dependency))
        }
        maybeBaseSha1 <- dependencyRepository.findSha1(repo)
        ignorableUpdates <- maybeBaseSha1 match {
          case Some(baseSha1) => pullRequestRepo.findUpdates(repo, baseSha1)
          case None           => F.pure(List.empty[Update])
        }
        updates1 = matchingUpdates.filterNot(
          update =>
            ignorableUpdates.exists(
              ignUpdate =>
                update.groupId == ignUpdate.groupId && update.nextVersion == ignUpdate.nextVersion &&
                  ignUpdate.artifactIds.exists(id => update.artifactId.startsWith(id))
            )
        )
        _ <- F.pure(println(repo + " " + updates1))
      } yield updates1.headOption.as(repo)
    }
}

object UpdateService {
  def isUpdateFor(update: Update.Single, dependency: Dependency): Boolean =
    update.groupId === dependency.groupId &&
      update.artifactId === dependency.artifactIdCross &&
      update.currentVersion === dependency.version

  def includeInUpdateCheck(dependency: Dependency): Boolean =
    (dependency.groupId, dependency.artifactId) match {
      case ("com.ccadllc.cedi", "build")                     => false
      case ("com.nrinaudo", "kantan.sbt-kantan")             => false
      case ("org.foundweekends.giter8", "sbt-giter8")        => false
      case ("org.scala-lang.modules", "sbt-scala-module")    => false
      case ("org.scala-lang.modules", "scala-module-plugin") => false
      case ("org.scodec", "scodec-build")                    => false
      case _                                                 => true
    }
}
