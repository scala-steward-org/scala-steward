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

import cats.effect._
import cats.implicits._
import cats.{Applicative, Parallel}
import coursier.cache.FileCache
import coursier.core.Project
import coursier.interop.cats._
import coursier.{Fetch, Info, Module, ModuleName, Organization, Versions}
import io.chrisdavenport.log4cats.Logger
import org.http4s.Uri
import org.scalasteward.core.data.{Dependency, ResolutionCtx, Resolver, Version}
import scala.concurrent.duration.FiniteDuration

/** An interface to [[https://get-coursier.io Coursier]] used for
  * fetching dependency versions and metadata.
  */
trait CoursierAlg[F[_]] {
  def getArtifactUrl(dependency: ResolutionCtx.Dep): F[Option[Uri]]

  def getVersions(dependency: ResolutionCtx.Dep): F[List[Version]]

  def getVersionsFresh(dependency: ResolutionCtx.Dep): F[List[Version]]

  final def getArtifactIdUrlMapping(dependencies: List[Dependency], resolvers: List[Resolver])(
      implicit F: Applicative[F]
  ): F[Map[String, Uri]] =
    dependencies
      .traverseFilter(dep =>
        getArtifactUrl(ResolutionCtx(dep, resolvers)).map(_.map(dep.artifactId.name -> _))
      )
      .map(_.toMap)
}

object CoursierAlg {
  def create[F[_]](cacheTtl: FiniteDuration)(
      implicit
      contextShift: ContextShift[F],
      logger: Logger[F],
      F: Sync[F]
  ): CoursierAlg[F] = {
    implicit val parallel: Parallel.Aux[F, F] = Parallel.identity[F]

    val cache: FileCache[F] = FileCache[F]().withTtl(cacheTtl)
    val cacheNoTtl: FileCache[F] = cache.withTtl(None)

    val fetch: Fetch[F] = Fetch[F](cache)

    val versions: Versions[F] = Versions[F](cache)
    val versionsNoTtl: Versions[F] = versions.withCache(cacheNoTtl)

    new CoursierAlg[F] {
      override def getArtifactUrl(dependency: ResolutionCtx.Dep): F[Option[Uri]] =
        dependency.resolvers.traverseFilter(convertResolver).flatMap { repositories =>
          getArtifactUrlImpl(toCoursierDependency(dependency.value), repositories)
        }

      private def getArtifactUrlImpl(
          dependency: coursier.Dependency,
          repositories: List[coursier.Repository]
      ): F[Option[Uri]] = {
        val fetchArtifacts = fetch
          .addArtifactTypes(coursier.Type.pom, coursier.Type.ivy)
          .addDependencies(dependency)
          .addRepositories(repositories: _*)
        fetchArtifacts.ioResult.attempt.flatMap {
          case Left(throwable) =>
            logger.debug(throwable)(s"Failed to fetch artifacts of $dependency").as(None)
          case Right(result) =>
            val maybeProject = result.resolution.projectCache
              .get(dependency.moduleVersion)
              .map { case (_, project) => project }
            maybeProject.traverseFilter { project =>
              getScmUrlOrHomePage(project.info) match {
                case Some(url) => F.pure(Some(url))
                case None =>
                  getParentDependency(project).traverseFilter(getArtifactUrlImpl(_, repositories))
              }
            }
        }
      }

      override def getVersions(dependency: ResolutionCtx.Dep): F[List[Version]] =
        getVersionsImpl(versions, dependency)

      override def getVersionsFresh(dependency: ResolutionCtx.Dep): F[List[Version]] =
        getVersionsImpl(versionsNoTtl, dependency)

      private def getVersionsImpl(
          versions: Versions[F],
          dependency: ResolutionCtx.Dep
      ): F[List[Version]] =
        dependency.resolvers.traverseFilter(convertResolver).flatMap { repositories =>
          versions
            .withModule(toCoursierModule(dependency.value))
            .withRepositories(repositories)
            .versions()
            .map(_.available.map(Version.apply).sorted)
            .handleErrorWith { throwable =>
              logger.debug(throwable)(s"Failed to get versions of $dependency").as(List.empty)
            }
        }

      private def convertResolver(resolver: Resolver): F[Option[coursier.Repository]] =
        toCoursierRepository(resolver) match {
          case Right(repository) => F.pure(Some(repository))
          case Left(message)     => logger.error(s"Failed to convert $resolver: $message").as(None)
        }
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

  private def toCoursierRepository(resolver: Resolver): Either[String, coursier.Repository] =
    resolver match {
      case Resolver.MavenRepository(_, location) =>
        Right(coursier.maven.MavenRepository.apply(location))
      case Resolver.IvyRepository(_, pattern) =>
        coursier.ivy.IvyRepository.parse(pattern)
    }

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
