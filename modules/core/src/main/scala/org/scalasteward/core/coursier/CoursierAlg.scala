/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.coursier

import cats.effect._
import cats.implicits._
import cats.{Applicative, Parallel}
import coursier.interop.cats._
import coursier.util.StringInterpolators.SafeIvyRepository
import coursier.{Info, Module, ModuleName, Organization}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.{Dependency, Version}
import scala.concurrent.duration._

/** An interface to [[https://get-coursier.io Coursier]] used for
  * fetching dependency versions and metadata.
  */
trait CoursierAlg[F[_]] {
  def getArtifactUrl(dependency: Dependency): F[Option[String]]

  def getNewerVersions(dependency: Dependency): F[List[Version]]

  final def getArtifactIdUrlMapping(dependencies: List[Dependency])(
      implicit F: Applicative[F]
  ): F[Map[String, String]] =
    dependencies
      .traverseFilter(dep => getArtifactUrl(dep).map(_.map(dep.artifactId -> _)))
      .map(_.toMap)
}

object CoursierAlg {
  def create[F[_]](
      implicit
      contextShift: ContextShift[F],
      logger: Logger[F],
      F: Sync[F]
  ): CoursierAlg[F] = {
    implicit val parallel: Parallel.Aux[F, F] = Parallel.identity[F]
    val cache = coursier.cache.FileCache[F]().withTtl(1.hour)
    val sbtPluginReleases =
      ivy"https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[defaultPattern]"
    val fetch = coursier.Fetch[F](cache).addRepositories(sbtPluginReleases)
    val versions = coursier.Versions[F](cache).addRepositories(sbtPluginReleases)

    new CoursierAlg[F] {
      override def getArtifactUrl(dependency: Dependency): F[Option[String]] = {
        val coursierDependency = toCoursierDependency(dependency)
        for {
          maybeFetchResult <- fetch
            .addDependencies(coursierDependency)
            .addArtifactTypes(coursier.Type.pom, coursier.Type.ivy)
            .ioResult
            .map(Option.apply)
            .handleErrorWith { throwable =>
              logger.debug(throwable)(s"Failed to fetch artifacts of $coursierDependency").as(None)
            }
        } yield {
          for {
            result <- maybeFetchResult
            moduleVersion = (coursierDependency.module, coursierDependency.version)
            (_, project) <- result.resolution.projectCache.get(moduleVersion)
            url <- getScmUrlOrHomePage(project.info)
          } yield url
        }
      }

      override def getNewerVersions(dependency: Dependency): F[List[Version]] = {
        val module = toCoursierModule(dependency)
        val version = Version(dependency.version)
        versions
          .withModule(module)
          .versions()
          .map(_.available.map(Version.apply).filter(_ > version))
          .handleErrorWith { throwable =>
            logger.error(throwable)(s"Failed to get newer versions of $module").as(List.empty)
          }
      }
    }
  }

  private def toCoursierDependency(dependency: Dependency): coursier.Dependency =
    coursier.Dependency(toCoursierModule(dependency), dependency.version).withTransitive(false)

  private def toCoursierModule(dependency: Dependency): Module =
    Module(
      Organization(dependency.groupId.value),
      ModuleName(dependency.artifactIdCross),
      dependency.attributes
    )

  private def getScmUrlOrHomePage(info: Info): Option[String] =
    (info.scm.flatMap(_.url).toList :+ info.homePage)
      .filterNot(url => url.isEmpty || url.startsWith("git@"))
      .headOption
}
