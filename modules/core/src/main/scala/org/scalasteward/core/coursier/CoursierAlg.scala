/*
 * Copyright 2018-2023 Scala Steward contributors
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
import cats.effect.Async
import cats.implicits._
import coursier.cache.{CachePolicy, FileCache}
import coursier.core.{Authentication, Project}
import coursier.{Fetch, Module, ModuleName, Organization}
import org.scalasteward.core.data.Resolver.Credentials
import org.scalasteward.core.data.{Dependency, Resolver, Version}
import org.scalasteward.core.util.uri
import org.typelevel.log4cats.Logger

/** An interface to [[https://get-coursier.io Coursier]] used for fetching dependency versions and
  * metadata.
  */
trait CoursierAlg[F[_]] {
  def getMetadata(dependency: Dependency, resolvers: List[Resolver]): F[DependencyMetadata]

  def getVersions(dependency: Dependency, resolver: Resolver): F[List[Version]]
}

object CoursierAlg {
  def create[F[_]](implicit
      logger: Logger[F],
      parallel: Parallel[F],
      F: Async[F]
  ): CoursierAlg[F] = {
    val fetch: Fetch[F] =
      Fetch[F](FileCache[F]())

    val cacheNoTtl: FileCache[F] =
      FileCache[F]().withTtl(None).withCachePolicies(List(CachePolicy.Update))

    new CoursierAlg[F] {
      override def getMetadata(
          dependency: Dependency,
          resolvers: List[Resolver]
      ): F[DependencyMetadata] =
        resolvers.traverseFilter(convertResolver(_).attempt.map(_.toOption)).flatMap {
          repositories =>
            val csrDependency = toCoursierDependency(dependency)
            getMetadataImpl(csrDependency, repositories, DependencyMetadata.empty)
        }

      private def getMetadataImpl(
          dependency: coursier.Dependency,
          repositories: List[coursier.Repository],
          acc: DependencyMetadata
      ): F[DependencyMetadata] = {
        val fetchArtifacts = fetch
          .withArtifactTypes(Set(coursier.Type.pom, coursier.Type.ivy))
          .withDependencies(List(dependency))
          .withRepositories(repositories)

        fetchArtifacts.ioResult.attempt.flatMap {
          case Left(throwable) =>
            logger.debug(throwable)(s"Failed to fetch artifacts of $dependency").as(acc)
          case Right(result) =>
            val maybeProject = result.resolution.projectCache
              .get(dependency.moduleVersion)
              .map { case (_, project) => project }

            maybeProject.fold(F.pure(acc)) { project =>
              val metadata = acc.enrichWith(metadataFrom(project))
              val recurse = Option.when(metadata.repoUrl.isEmpty)(())
              (recurse >> parentOf(project)).fold(F.pure(metadata)) { parent =>
                getMetadataImpl(parent, repositories, metadata)
              }
            }
        }
      }

      override def getVersions(dependency: Dependency, resolver: Resolver): F[List[Version]] =
        convertResolver(resolver).flatMap { repository =>
          val module = toCoursierModule(dependency)
          repository.versions(module, cacheNoTtl.fetch).run.flatMap {
            case Left(message) =>
              logger.debug(message) >> F.raiseError[List[Version]](new Throwable(message))
            case Right((versions, _)) =>
              F.pure(versions.available.map(Version.apply).sorted)
          }
        }

      private def convertResolver(resolver: Resolver): F[coursier.Repository] =
        toCoursierRepository(resolver) match {
          case Right(repository) => F.pure(repository)
          case Left(message) =>
            logger.error(s"Failed to convert $resolver: $message") >>
              F.raiseError[coursier.Repository](new Throwable(message))
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
        val authentication = toCoursierAuthentication(creds, headers)
        Right(coursier.maven.SbtMavenRepository.apply(location, authentication))
      case Resolver.IvyRepository(_, pattern, creds, headers) =>
        val authentication = toCoursierAuthentication(creds, headers)
        coursier.ivy.IvyRepository.parse(pattern, authentication = authentication)
    }

  private def toCoursierAuthentication(
      credentials: Option[Credentials],
      headers: List[Resolver.Header]
  ): Option[Authentication] =
    Option.when(credentials.nonEmpty || headers.nonEmpty) {
      new Authentication(
        credentials.fold("")(_.user),
        credentials.map(_.pass),
        headers.map(h => (h.key, h.value)),
        optional = false,
        realmOpt = None,
        httpsOnly = true,
        passOnRedirect = false
      )
    }

  private def metadataFrom(project: Project): DependencyMetadata =
    DependencyMetadata(
      homePage = uri.fromStringWithScheme(project.info.homePage),
      scmUrl = project.info.scm.flatMap(_.url).flatMap(uri.fromStringWithScheme),
      releaseNotesUrl = project.properties
        .collectFirst { case (key, value) if key.equalsIgnoreCase("info.releaseNotesUrl") => value }
        .flatMap(uri.fromStringWithScheme),
      versionScheme = project.properties
        .collectFirst { case (key, value) if key.equalsIgnoreCase("info.versionScheme") => value }
    )

  private def parentOf(project: Project): Option[coursier.Dependency] =
    project.parent.map { case (module, version) =>
      coursier.Dependency(module, version).withTransitive(false)
    }
}
