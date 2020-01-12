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

package org.scalasteward.core.coursier

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import cats.{Applicative, Parallel}
import coursier.core.Project
import coursier.interop.cats._
import coursier.maven.MavenRepository
import coursier.util.StringInterpolators.SafeIvyRepository
import coursier.{Info, Module, ModuleName, Organization}
import io.chrisdavenport.log4cats.Logger
import org.http4s.Uri
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.{Dependency, Resolver, Version}

/** An interface to [[https://get-coursier.io Coursier]] used for
  * fetching dependency versions and metadata.
  */
trait CoursierAlg[F[_]] {
  def getArtifactUrl(dependency: Dependency, resolvers: List[Resolver]): F[Option[Uri]]

  def getVersions(dependency: Dependency, resolvers: List[Resolver]): F[List[Version]]

  final def getArtifactIdUrlMapping(dependencies: List[Dependency], resolvers: List[Resolver])(
      implicit F: Applicative[F]
  ): F[Map[String, Uri]] =
    dependencies
      .traverseFilter(dep => getArtifactUrl(dep, resolvers).map(_.map(dep.artifactId.name -> _)))
      .map(_.toMap)
}

object CoursierAlg {
  def create[F[_]](
      implicit
      config: Config,
      contextShift: ContextShift[F],
      logger: Logger[F],
      F: Sync[F]
  ): CoursierAlg[F] = {
    implicit val parallel: Parallel.Aux[F, F] = Parallel.identity[F]
    val cache = coursier.cache.FileCache[F]().withTtl(config.cacheTtl)
    val sbtPluginReleases =
      ivy"https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[defaultPattern]"
    val fetch = coursier.Fetch[F](cache).withRepositories(List(sbtPluginReleases))
    val versions = coursier.Versions[F](cache).withRepositories(List(sbtPluginReleases))

    new CoursierAlg[F] {
      override def getArtifactUrl(
          dependency: Dependency,
          resolvers: List[Resolver]
      ): F[Option[Uri]] =
        getArtifactUrlImpl(toCoursierDependency(dependency), resolvers)

      private def getArtifactUrlImpl(
          coursierDependency: coursier.Dependency,
          resolvers: List[Resolver]
      ): F[Option[Uri]] =
        (for {
          maybeFetchResult <- fetch
            .addRepositories(resolvers.map(toCoursierRepository): _*)
            .addDependencies(coursierDependency)
            .addArtifactTypes(coursier.Type.pom, coursier.Type.ivy)
            .ioResult
            .map(Option.apply)
            .handleErrorWith { throwable =>
              logger.debug(throwable)(s"Failed to fetch artifacts of $coursierDependency").as(None)
            }
        } yield {
          (for {
            result <- maybeFetchResult.toOptionT[F]
            moduleVersion = (coursierDependency.module, coursierDependency.version)
            (_, project) <- result.resolution.projectCache.get(moduleVersion).toOptionT[F]
            parentUrl = getParentDependency(project)
              .traverseFilter(getArtifactUrlImpl(_, resolvers))
            url <- getScmUrlOrHomePage(project.info).toOptionT[F].orElse(OptionT(parentUrl))
          } yield url).value
        }).flatten

      override def getVersions(
          dependency: Dependency,
          resolvers: List[Resolver]
      ): F[List[Version]] =
        versions
          .addRepositories(resolvers.map(toCoursierRepository): _*)
          .withModule(toCoursierModule(dependency))
          .versions()
          .map(_.available.map(Version.apply).sorted)
    }
  }

  private def toCoursierDependency(dependency: Dependency): coursier.Dependency =
    coursier.Dependency(toCoursierModule(dependency), dependency.version).withTransitive(false)

  private def toCoursierModule(dependency: Dependency): Module =
    Module(
      Organization(dependency.groupId.value),
      ModuleName(dependency.artifactId.crossName),
      dependency.attributes
    )

  private def toCoursierRepository(resolver: Resolver): coursier.Repository =
    MavenRepository.apply(resolver.location)

  private def getParentDependency(project: Project): Option[coursier.Dependency] =
    project.parent.map {
      case (module, version) =>
        coursier.Dependency(module, version).withTransitive(false)
    }

  private def getScmUrlOrHomePage(info: Info): Option[Uri] =
    (info.scm.flatMap(_.url).toList :+ info.homePage)
      .filterNot(url => url.isEmpty || url.startsWith("git@") || url.startsWith("git:"))
      .flatMap(Uri.fromString(_).toList.filter(_.scheme.isDefined))
      .headOption
}
