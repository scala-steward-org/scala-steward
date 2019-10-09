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

package org.scalasteward.core.update

import cats.implicits._
import cats.{Monad, TraverseFilter}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.{Update, Version}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.FilterAlg._
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

final class FilterAlg[F[_]](
    implicit
    logger: Logger[F],
    F: Monad[F]
) {
  def globalFilterMany[G[_]: TraverseFilter](updates: G[Update.Single]): F[G[Update.Single]] =
    updates.traverseFilter(update => logIfRejected(globalFilter(update)))

  def localFilterMany[G[_]: TraverseFilter](
      config: RepoConfig,
      updates: G[Update.Single]
  ): F[G[Update.Single]] =
    updates.traverseFilter(update => logIfRejected(localFilter(update, config)))

  private def logIfRejected(result: FilterResult): F[Option[Update.Single]] =
    result match {
      case Right(update) => F.pure(update.some)
      case Left(reason) =>
        logger.info(s"Ignore ${reason.update.show} (reason: ${reason.show})") *> F.pure(None)
    }
}

object FilterAlg {
  type FilterResult = Either[RejectionReason, Update.Single]

  sealed trait RejectionReason {
    def update: Update.Single
    def show: String = this match {
      case IgnoredGlobally(_)       => "ignored globally"
      case IgnoredByConfig(_)       => "ignored by config"
      case NotAllowedByConfig(_)    => "not allowed by config"
      case BadVersions(_)           => "bad versions"
      case NoSuitableNextVersion(_) => "no suitable next version"
    }
  }

  final case class IgnoredGlobally(update: Update.Single) extends RejectionReason
  final case class IgnoredByConfig(update: Update.Single) extends RejectionReason
  final case class NotAllowedByConfig(update: Update.Single) extends RejectionReason
  final case class BadVersions(update: Update.Single) extends RejectionReason
  final case class NoSuitableNextVersion(update: Update.Single) extends RejectionReason

  def globalFilter(update: Update.Single): FilterResult =
    removeBadVersions(update)
      .flatMap(isIgnoredGlobally)
      .flatMap(selectSuitableNextVersion)

  def localFilter(update: Update.Single, repoConfig: RepoConfig): FilterResult =
    globalFilter(update).flatMap(repoConfig.updates.keep)

  def isIgnoredGlobally(update: Update.Single): FilterResult = {
    val keep = ((update.groupId.value, update.artifactId) match {
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

  def selectSuitableNextVersion(update: Update.Single): FilterResult = {
    val newerVersions = update.newerVersions.map(Version.apply).toList
    val maybeNext = Version(update.currentVersion).selectNext(newerVersions)
    maybeNext match {
      case Some(next) => Right(update.copy(newerVersions = Nel.of(next.value)))
      case None       => Left(NoSuitableNextVersion(update))
    }
  }

  def removeBadVersions(update: Update.Single): FilterResult =
    util
      .removeAll(update.newerVersions, badVersions(update))
      .map(versions => update.copy(newerVersions = versions))
      .fold[FilterResult](Left(BadVersions(update)))(Right.apply)

  private def badVersions(update: Update.Single): List[String] =
    (update.groupId.value, update.artifactId, update.currentVersion) match {
      // https://github.com/vlovgr/ciris/pull/182#issuecomment-420599759
      case ("com.jsuereth", "sbt-pgp", "1.1.2-1") => List("1.1.2")
      case ("commons-collections", "commons-collections", _) =>
        List(
          // https://github.com/albuch/sbt-dependency-check/pull/85
          "20040102.233541"
        )
      case ("io.monix", _, _) =>
        List(
          // https://github.com/fthomas/scala-steward/issues/105
          "3.0.0-fbcb270"
        )
      case ("net.sourceforge.plantuml", "plantuml", _) =>
        List(
          // https://github.com/esamson/remder/pull/5
          "8059",
          // https://github.com/metabookmarks/sbt-plantuml-plugin/pull/10
          "2017.11"
        )
      case ("org.http4s", _, _) =>
        List(
          // https://github.com/http4s/http4s/pull/2153
          "0.19.0"
        )
      case _ => List.empty
    }
}
