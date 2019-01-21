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
import cats.{Applicative, TraverseFilter}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.github.data.Repo
import org.scalasteward.core.model.Update
import org.scalasteward.core.util

trait FilterAlg[F[_]] {
  def globalKeep(update: Update.Single): Boolean

  def globalFilter(update: Update.Single): F[Option[Update.Single]]

  def localKeep(repo: Repo, update: Update.Single): Boolean

  def localFilter(repo: Repo, update: Update.Single): F[Option[Update.Single]]

  final def globalFilterMany[G[_]: TraverseFilter](updates: G[Update.Single])(
      implicit F: Applicative[F]
  ): F[G[Update.Single]] =
    updates.traverseFilter(globalFilter)

  final def localFilterMany[G[_]: TraverseFilter](repo: Repo, updates: G[Update.Single])(
      implicit F: Applicative[F]
  ): F[G[Update.Single]] =
    updates.traverseFilter(update => localFilter(repo, update))
}

object FilterAlg {
  def create[F[_]](implicit logger: Logger[F], F: Applicative[F]): FilterAlg[F] =
    new FilterAlg[F] {
      override def globalKeep(update: Update.Single): Boolean = {
        (update.groupId, update.artifactId) match {
          case ("org.scala-lang", "scala-compiler") => false
          case ("org.scala-lang", "scala-library")  => false
          case ("org.scala-lang", "scala-reflect")  => false

          case ("org.typelevel", "scala-library") => false

          case _ => true
        }
      } && {
        update.configurations.fold("")(_.toLowerCase) match {
          case "phantom-js-jetty"    => false
          case "scalafmt"            => false
          case "scripted-sbt"        => false
          case "scripted-sbt-launch" => false
          case "tut"                 => false
          case _                     => true
        }
      }

      override def localKeep(repo: Repo, update: Update.Single): Boolean =
        (repo.show, update.groupId, update.artifactId) match {
          case ("scala/scala-dist", "com.amazonaws", "aws-java-sdk-s3") => false

          // https://github.com/scala/scala-dist/pull/200#issuecomment-440720981
          case ("scala/scala-dist", "com.typesafe.sbt", "sbt-native-packager") => false

          case _ => true
        }

      override def globalFilter(update: Update.Single): F[Option[Update.Single]] =
        filterImpl(globalKeep(update), update)

      override def localFilter(repo: Repo, update: Update.Single): F[Option[Update.Single]] =
        filterImpl(globalKeep(update) && localKeep(repo, update), update)

      def filterImpl(keep: Boolean, update: Update.Single): F[Option[Update.Single]] =
        removeBadVersions(update).filter(_ => keep) match {
          case none @ None    => logger.info(s"Ignore ${update.show}") *> F.pure(none)
          case some @ Some(_) => F.pure(some)
        }
    }

  def badVersions(update: Update.Single): List[String] =
    (update.groupId, update.artifactId, update.currentVersion, update.nextVersion) match {
      // https://github.com/vlovgr/ciris/pull/182#issuecomment-420599759
      case ("com.jsuereth", "sbt-pgp", "1.1.2-1", "1.1.2") => List("1.1.2")

      case ("io.monix", _, _, _) =>
        List(
          // https://github.com/fthomas/scala-steward/issues/105
          "3.0.0-fbcb270"
        )
      case ("net.sourceforge.plantuml", "plantuml", _, _) =>
        List(
          // https://github.com/esamson/remder/pull/5
          "8059"
        )
      case ("org.http4s", _, _, _) =>
        List(
          // https://github.com/http4s/http4s/pull/2153
          "0.19.0"
        )
      case ("org.scalatest", "scalatest", _, _) =>
        List(
          // https://github.com/lightbend/migration-manager/pull/260
          "3.2.0-SNAP10"
        )

      case _ => List.empty
    }

  def removeBadVersions(update: Update.Single): Option[Update.Single] =
    util
      .removeAll(update.newerVersions, badVersions(update))
      .map(versions => update.copy(newerVersions = versions))
}
