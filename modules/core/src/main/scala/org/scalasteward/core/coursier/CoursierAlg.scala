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

import cats.Parallel
import cats.effect._
import cats.implicits._
import coursier.interop.cats._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.Dependency
import scala.concurrent.ExecutionContext

trait CoursierAlg[F[_]] {
  def getArtifactUrl(dependency: Dependency): F[Option[String]]
  def getArtifactIdUrlMapping(dependencies: List[Dependency]): F[Map[String, String]]
}

object CoursierAlg {
  def create[F[_]](
      implicit
      logger: Logger[F],
      F: Sync[F]
  ): CoursierAlg[F] = {
    implicit val P = Parallel.identity[F]
    implicit val cs: ContextShift[F] = new ContextShift[F] {
      override def shift: F[Unit] = F.unit
      override def evalOn[A](ec: ExecutionContext)(fa: F[A]): F[A] = F.defer(fa)
    }
    val cache = coursier.cache.FileCache[F]()
    val fetch = coursier.Fetch[F](cache)
    new CoursierAlg[F] {
      override def getArtifactUrl(dependency: Dependency): F[Option[String]] = {
        val module = coursier.Module(
          coursier.Organization(dependency.groupId.value),
          coursier.ModuleName(dependency.artifactIdCross)
        )
        val coursierDependency =
          coursier.Dependency.of(module, dependency.version).withTransitive(false)
        for {
          maybeFetchResult <- fetch
            .addDependencies(coursierDependency)
            .addArtifactTypes(coursier.Type.pom)
            .ioResult
            .map(Option.apply)
            .handleErrorWith { throwable =>
              logger.debug(throwable)(s"Failed to fetch POM of $coursierDependency").as(None)
            }
        } yield {
          for {
            result <- maybeFetchResult
            (_, project) <- result.resolution.projectCache.get((module, dependency.version))
            maybeScmUrl = project.info.scm.flatMap(_.url).filter(_.nonEmpty)
            maybeHomepage = Option(project.info.homePage).filter(_.nonEmpty)
            url <- maybeScmUrl.orElse(maybeHomepage)
          } yield url
        }
      }

      override def getArtifactIdUrlMapping(dependencies: List[Dependency]): F[Map[String, String]] =
        dependencies
          .traverseFilter(dep => getArtifactUrl(dep).map(_.map(dep.artifactId -> _)))
          .map(_.toMap)
    }
  }
}
