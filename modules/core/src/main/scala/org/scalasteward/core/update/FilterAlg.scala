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
import cats.{Monad, TraverseFilter}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.model.Update
import org.scalasteward.core.repoconfig.{RepoConfig, RepoConfigAlg}
import org.scalasteward.core.update.FilterAlg._
import org.scalasteward.core.util

class FilterAlg[F[_]](
    implicit
    logger: Logger[F],
    repoConfigAlg: RepoConfigAlg[F],
    F: Monad[F]
) {
  def globalFilterMany[G[_]: TraverseFilter](updates: G[Update.Single]): F[G[Update.Single]] =
    updates.traverseFilter(update => logIfRejected(globalFilter(update)))

  def localFilterManyWithConfig[G[_]: TraverseFilter](
      config: RepoConfig,
      updates: G[Update.Single]
  ): F[G[Update.Single]] =
    updates.traverseFilter(update => logIfRejected(localFilter(update, config)))

  def localFilterMany[G[_]: TraverseFilter](
      repo: Repo,
      updates: G[Update.Single]
  ): F[G[Update.Single]] =
    repoConfigAlg.getRepoConfig(repo).flatMap(localFilterManyWithConfig(_, updates))

  private def logIfRejected(result: FilterResult): F[Option[Update.Single]] =
    result match {
      case Right(update) => F.pure(update.some)
      case Left(reason)  => logger.info(s"Ignore ${reason.update.show}") *> F.pure(None)
    }
}

object FilterAlg {
  type FilterResult = Either[RejectionReason, Update.Single]

  sealed trait RejectionReason { def update: Update.Single }
  final case class IgnoredGlobally(update: Update.Single) extends RejectionReason
  final case class IgnoredByConfig(update: Update.Single) extends RejectionReason
  final case class NotAllowedByConfig(update: Update.Single) extends RejectionReason
  final case class BadVersions(update: Update.Single) extends RejectionReason
  final case class NonSnapshotToSnapshotUpdate(update: Update.Single) extends RejectionReason

  def globalFilter(update: Update.Single): FilterResult =
    removeBadVersions(update)
      .flatMap(isIgnoredGlobally)
      .flatMap(ignoreNonSnapshotToSnapshotUpdate)

  def localFilter(update: Update.Single, repoConfig: RepoConfig): FilterResult =
    globalFilter(update).flatMap(repoConfig.updates.keep)

  def isIgnoredGlobally(update: Update.Single): FilterResult = {
    val keep = ((update.groupId, update.artifactId) match {
      case ("org.scala-lang", "scala-compiler") => false
      case ("org.scala-lang", "scala-library")  => false
      case ("org.scala-lang", "scala-reflect")  => false
      case ("org.typelevel", "scala-library")   => false
      case _                                    => true
    }) && (update.configurations.fold("")(_.toLowerCase) match {
      case "phantom-js-jetty"    => false
      case "scalafmt"            => false
      case "scripted-sbt"        => false
      case "scripted-sbt-launch" => false
      case "tut"                 => false
      case _                     => true
    })
    if (keep) Right(update) else Left(IgnoredGlobally(update))
  }

  def ignoreNonSnapshotToSnapshotUpdate(update: Update.Single): FilterResult = {
    val snap = "-SNAP"
    if (update.newerVersions.head.contains(snap) && !update.currentVersion.contains(snap))
      Left(NonSnapshotToSnapshotUpdate(update))
    else
      Right(update)
  }

  def removeBadVersions(update: Update.Single): FilterResult =
    util
      .removeAll(update.newerVersions, badVersions(update))
      .map(versions => update.copy(newerVersions = versions))
      .fold[FilterResult](Left(BadVersions(update)))(Right.apply)

  private def badVersions(update: Update.Single): List[String] =
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
}
