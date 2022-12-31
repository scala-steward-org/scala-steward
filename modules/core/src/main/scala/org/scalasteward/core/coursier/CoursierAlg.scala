/*
 * Copyright 2018-2022 Scala Steward contributors
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

import cats.effect.Async
import cats.implicits._
import cats.{Applicative, Parallel}
import coursier.cache.{CachePolicy, FileCache}
import coursier.core.{Authentication, Project}
import coursier.{Fetch, Info, Module, ModuleName, Organization}
import org.http4s.Uri
import org.scalasteward.core.data.Resolver.Credentials
import org.scalasteward.core.data.{Dependency, Resolver, Scope, Version}
import org.scalasteward.core.util.uri
import org.typelevel.log4cats.Logger

/** An interface to [[https://get-coursier.io Coursier]] used for fetching dependency versions and
  * metadata.
  */
trait CoursierAlg[F[_]] {
  def getArtifactUrl(dependency: Scope.Dependency): F[Option[Uri]]

  def getVersions(dependency: Dependency, resolver: Resolver): F[List[Version]]

  final def getArtifactIdUrlMapping(dependencies: Scope.Dependencies)(implicit
      F: Applicative[F]
  ): F[Map[String, Uri]] =
    dependencies.sequence
      .traverseFilter(dep => getArtifactUrl(dep).map(_.map(dep.value.artifactId.name -> _)))
      .map(_.toMap)
}

object CoursierAlg {
  def create[F[_]](implicit
      logger: Logger[F],
      parallel: Parallel[F],
      F: Async[F]
  ): CoursierAlg[F] = {
    val fetch: Fetch[F] = Fetch[F](FileCache[F]())

    val cacheNoTtl: FileCache[F] =
      FileCache[F]().withTtl(None).withCachePolicies(List(CachePolicy.Update))

    new CoursierAlg[F] {
      override def getArtifactUrl(dependency: Scope.Dependency): F[Option[Uri]] =
        convertToCoursierTypes(dependency).flatMap((getArtifactUrlImpl _).tupled)

      private def getArtifactUrlImpl(
          dependency: coursier.Dependency,
          repositories: List[coursier.Repository]
      ): F[Option[Uri]] = {
        val fetchArtifacts = fetch
          .withArtifactTypes(Set(coursier.Type.pom, coursier.Type.ivy))
          .withDependencies(List(dependency))
          .withRepositories(repositories)
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

      override def getVersions(dependency: Dependency, resolver: Resolver): F[List[Version]] =
        toCoursierRepository(resolver) match {
          case Left(message) =>
            logger.error(message) >> F.raiseError(new Throwable(message))
          case Right(repository) =>
            val module = toCoursierModule(dependency)
            repository.versions(module, cacheNoTtl.fetch).run.flatMap {
              case Left(message) =>
                logger.debug(message) >> F.raiseError(new Throwable(message))
              case Right((versions, _)) => F.pure(versions.available.map(Version.apply).sorted)
            }
        }

      private def convertToCoursierTypes(
          dependency: Scope.Dependency
      ): F[(coursier.Dependency, List[coursier.Repository])] =
        dependency.resolvers.traverseFilter(convertResolver).map { repositories =>
          (toCoursierDependency(dependency.value), repositories)
        }

      private def convertResolver(resolver: Resolver): F[Option[coursier.Repository]] =
        toCoursierRepository(resolver) match {
          case Right(repository) => F.pure(Some(repository))
          case Left(message)     => logger.error(s"Failed to convert $resolver: $message").as(None)
        }
    }
  }

  private def toCoursierDependency(dependency: Dependency): coursier.Dependency = {
    val module = toCoursierModule(dependency)
    coursier.Dependency(module, dependency.version.value).withTransitive(false)
  }

  private def toCoursierModule(dependency: Dependency): Module =
    Module(
      Organization(dependency.groupId.value),
      ModuleName(dependency.artifactId.crossName),
      dependency.attributes
    )

  private def toCoursierRepository(resolver: Resolver): Either[String, coursier.Repository] =
    resolver match {
      case Resolver.MavenRepository(_, location, creds, headers) =>
        Right(
          coursier.maven.MavenRepository
            .apply(location, toCoursierAuthentication(creds, headers))
        )
      case Resolver.IvyRepository(_, pattern, creds, headers) =>
        coursier.ivy.IvyRepository
          .parse(pattern, authentication = toCoursierAuthentication(creds, headers))
    }

  private def toCoursierAuthentication(
      credentials: Option[Credentials],
      headers: List[Resolver.Header]
  ): Option[Authentication] =
    if (credentials.isEmpty && headers.isEmpty) {
      None
    } else {
      Some(
        new Authentication(
          credentials.fold("")(_.user),
          credentials.map(_.pass),
          headers.map(h => (h.key, h.value)),
          optional = false,
          None,
          httpsOnly = true,
          passOnRedirect = false
        )
      )
    }

  private def getParentDependency(project: Project): Option[coursier.Dependency] =
    project.parent.map { case (module, version) =>
      coursier.Dependency(module, version).withTransitive(false)
    }

  private def getScmUrlOrHomePage(info: Info): Option[Uri] =
    uri.findBrowsableUrl(info.scm.flatMap(_.url).toList :+ info.homePage)
}
