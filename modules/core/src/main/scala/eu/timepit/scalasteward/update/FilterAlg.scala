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
import cats.{Applicative, TraverseFilter}
import eu.timepit.scalasteward.github.data.Repo
import eu.timepit.scalasteward.model.Update
import io.chrisdavenport.log4cats.Logger

trait FilterAlg[F[_]] {
  def globalKeep(update: Update): Boolean

  def globalFilter(update: Update): F[Option[Update]]

  def localKeep(repo: Repo, update: Update): Boolean

  def localFilter(repo: Repo, update: Update): F[Option[Update]]

  final def globalFilterMany[G[_]: TraverseFilter](updates: G[Update])(
      implicit F: Applicative[F]
  ): F[G[Update]] =
    updates.traverseFilter(globalFilter)

  final def localFilterMany[G[_]: TraverseFilter](repo: Repo, updates: G[Update])(
      implicit F: Applicative[F]
  ): F[G[Update]] =
    updates.traverseFilter(update => localFilter(repo, update))
}

object FilterAlg {
  def create[F[_]](implicit logger: Logger[F], F: Applicative[F]): FilterAlg[F] =
    new FilterAlg[F] {
      override def globalKeep(update: Update): Boolean =
        (update.groupId, update.artifactId, update.nextVersion) match {
          case ("org.scala-lang", "scala-compiler", _) => false
          case ("org.scala-lang", "scala-library", _)  => false
          case ("org.scala-lang", "scala-reflect", _)  => false

          case ("org.eclipse.jetty", "jetty-server", _)    => false
          case ("org.eclipse.jetty", "jetty-websocket", _) => false

          // transitive dependencies of e.g. com.lucidchart:sbt-scalafmt
          case ("com.geirsson", "scalafmt-cli_2.11", _)  => false
          case ("com.geirsson", "scalafmt-core_2.12", _) => false

          // https://github.com/fthomas/scala-steward/issues/105
          case ("io.monix", _, "3.0.0-fbcb270") => false

          // https://github.com/esamson/remder/pull/5
          case ("net.sourceforge.plantuml", "plantuml", "8059") => false

          // https://github.com/http4s/http4s/pull/2153
          case ("org.http4s", _, "0.19.0") => false

          // https://github.com/lightbend/migration-manager/pull/260
          case ("org.scalatest", "scalatest", "3.2.0-SNAP10") => false

          case _ => true
        }

      override def localKeep(repo: Repo, update: Update): Boolean =
        (repo.show, update.groupId, update.artifactId) match {
          case ("scala/scala-dist", "com.amazonaws", "aws-java-sdk-s3") => false
          case _                                                        => true
        }

      override def globalFilter(update: Update): F[Option[Update]] =
        filterImpl(globalKeep(update), update)

      override def localFilter(repo: Repo, update: Update): F[Option[Update]] =
        filterImpl(globalKeep(update) && localKeep(repo, update), update)

      def filterImpl(keep: Boolean, update: Update): F[Option[Update]] =
        if (keep) F.pure(Some(update))
        else logger.info(s"Ignore ${update.show}") *> F.pure(None)
    }
}
