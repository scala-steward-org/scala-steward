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

import java.util.concurrent.ExecutorService

import cats.effect._
import cats.implicits._
import org.scalasteward.core.data.Dependency

trait CoursierAlg[F[_]] {
  def getArtifactUrl(dependency: Dependency): F[Option[String]]
  def getArtifactIdUrlMapping(dependencies: List[Dependency]): F[Map[String, String]]
}

object CoursierAlg {
  // TODO: Use coursier.interop.cats._ in https://github.com/coursier/coursier/pull/1294 when published
  implicit def createCoursierSync[F[_]](
      implicit
      F: Sync[F]
  ): coursier.util.Sync[F] = new coursier.util.Sync[F] {
    override def point[A](a: A): F[A] = F.point(a)
    override def bind[A, B](elem: F[A])(f: A => F[B]): F[B] = F.flatMap(elem)(f)
    override def delay[A](a: => A): F[A] = F.delay(a)
    override def fromAttempt[A](a: Either[Throwable, A]): F[A] = F.fromEither(a)
    override def handle[A](a: F[A])(f: PartialFunction[Throwable, A]): F[A] =
      F.handleError(a)(f.apply)
    override def gather[A](elems: Seq[F[A]]): F[Seq[A]] =
      elems.foldLeft(F.delay(Seq.empty[A])) {
        case (fseq, f) =>
          fseq.flatMap(seq => f.map(seq :+ _))
      }
    override def schedule[A](unused: ExecutorService)(f: => A): F[A] = F.delay(f)
  }

  def create[F[_]](
      implicit
      F: Sync[F]
  ): CoursierAlg[F] = {
    val cache = coursier.cache.FileCache[F]()
    val fetch = coursier.Fetch[F](cache)
    new CoursierAlg[F] {
      override def getArtifactUrl(dependency: Dependency): F[Option[String]] = {
        val module = coursier.Module(
          coursier.Organization(dependency.groupId),
          coursier.ModuleName(dependency.artifactIdCross)
        )
        for {
          maybeFetchResult <- fetch
            .addDependencies(
              coursier.Dependency.of(module, dependency.version).withTransitive(false)
            )
            .addArtifactTypes(coursier.Type.pom)
            .ioResult
            .map(Option.apply)
            .recover {
              case _: coursier.error.ResolutionError => None
            }
        } yield {
          maybeFetchResult.flatMap(
            _.resolution.projectCache.get((module, dependency.version)).flatMap {
              case (_, project) =>
                // TODO: use Scm info if published https://github.com/coursier/coursier/pull/1291
                Option(project.info.homePage)
            }
          )
        }
      }

      override def getArtifactIdUrlMapping(dependencies: List[Dependency]): F[Map[String, String]] =
        for {
          entries <- dependencies.traverse(dep => {
            getArtifactUrl(dep).map(dep.artifactId -> _.getOrElse(""))
          })
        } yield Map(entries.filter { case (_, url) => url =!= "" }: _*)
    }
  }
}
