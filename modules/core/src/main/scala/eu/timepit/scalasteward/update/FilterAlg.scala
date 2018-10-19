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
  def filter(repo: Repo, update: Update): F[Option[Update]]

  def filterMany[G[_]: TraverseFilter](repo: Repo, updates: G[Update])(
      implicit F: Applicative[F]
  ): F[G[Update]] =
    updates.traverseFilter(update => filter(repo, update))
}

object FilterAlg {
  def create[F[_]](implicit logger: Logger[F], F: Applicative[F]): FilterAlg[F] =
    new FilterAlg[F] {
      override def filter(repo: Repo, update: Update): F[Option[Update]] = {
        val globalKeep = (update.groupId, update.artifactId) match {
          case ("org.scala-lang", "scala-compiler")     => false
          case ("org.scala-lang", "scala-library")      => false
          case ("org.eclipse.jetty", "jetty-server")    => false
          case ("org.eclipse.jetty", "jetty-websocket") => false
          case _                                        => true
        }
        val localKeep = (repo.show, update.groupId, update.artifactId) match {
          case ("scala/scala-dist", "com.amazonaws", "aws-java-sdk-s3") => false
          case _                                                        => true
        }

        if (globalKeep && localKeep) F.pure(Some(update))
        else logger.info(s"Ignore update ${update.show}") *> F.pure(None)
      }
    }
}
