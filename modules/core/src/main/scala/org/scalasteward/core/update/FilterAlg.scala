/*
 * Copyright 2018-2021 Scala Steward contributors
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

import cats.syntax.all._
import cats.{Monad, TraverseFilter}
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data._
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.FilterAlg._
import org.scalasteward.core.util.Nel

final class FilterAlg[F[_]](implicit
    logger: Logger[F],
    F: Monad[F]
) {
  def localFilterMany[G[_]: TraverseFilter](
      config: RepoConfig,
      updates: G[Update.Single]
  ): F[G[Update.Single]] =
    updates.traverseFilter(update => logIfRejected(localFilter(update, config)))

  private def logIfRejected(result: FilterResult): F[Option[Update.Single]] =
    result match {
      case Right(update) => F.pure(update.some)
      case Left(reason) =>
        logger.info(s"Ignore ${reason.update.show} (reason: ${reason.show})").as(None)
    }
}

object FilterAlg {
  type FilterResult = Either[RejectionReason, Update.Single]

  sealed trait RejectionReason {
    def update: Update.Single
    def show: String =
      this match {
        case IgnoredByConfig(_)         => "ignored by config"
        case VersionPinnedByConfig(_)   => "version is pinned by config"
        case NotAllowedByConfig(_)      => "not allowed by config"
        case BadVersions(_)             => "bad versions"
        case NoSuitableNextVersion(_)   => "no suitable next version"
        case VersionOrderingConflict(_) => "version ordering conflict"
      }
  }

  final case class IgnoredByConfig(update: Update.Single) extends RejectionReason
  final case class VersionPinnedByConfig(update: Update.Single) extends RejectionReason
  final case class NotAllowedByConfig(update: Update.Single) extends RejectionReason
  final case class BadVersions(update: Update.Single) extends RejectionReason
  final case class NoSuitableNextVersion(update: Update.Single) extends RejectionReason
  final case class VersionOrderingConflict(update: Update.Single) extends RejectionReason

  def localFilter(update: Update.Single, repoConfig: RepoConfig): FilterResult =
    repoConfig.updates.keep(update).flatMap(globalFilter)

  private def globalFilter(update: Update.Single): FilterResult =
    removeBadVersions(update)
      .flatMap(selectSuitableNextVersion)
      .flatMap(checkVersionOrdering)

  def isScalaDependency(dependency: Dependency): Boolean =
    isScalaDependency(dependency.groupId.value, dependency.artifactId.name)

  def isScalaDependency(groupId: String, artifactId: String): Boolean =
    (groupId, artifactId) match {
      case ("org.scala-lang", "scala-compiler") => true
      case ("org.scala-lang", "scala-library")  => true
      case ("org.scala-lang", "scala-reflect")  => true
      case ("org.scala-lang", "scalap")         => true
      case ("org.typelevel", "scala-library")   => true
      case _                                    => false
    }

  def isScalaDependencyIgnored(dependency: Dependency, ignoreScalaDependency: Boolean): Boolean =
    ignoreScalaDependency && isScalaDependency(dependency)

  def isDependencyConfigurationIgnored(dependency: Dependency): Boolean =
    dependency.configurations.fold("")(_.toLowerCase) match {
      case "phantom-js-jetty"    => true
      case "scalafmt"            => true
      case "scripted-sbt"        => true
      case "scripted-sbt-launch" => true
      case "tut"                 => true
      case _                     => false
    }

  private def selectSuitableNextVersion(update: Update.Single): FilterResult = {
    val newerVersions = update.newerVersions.map(Version.apply).toList
    val maybeNext = Version(update.currentVersion).selectNext(newerVersions)
    maybeNext match {
      case Some(next) => Right(update.copy(newerVersions = Nel.of(next.value)))
      case None       => Left(NoSuitableNextVersion(update))
    }
  }

  private def checkVersionOrdering(update: Update.Single): FilterResult = {
    val (current, next) =
      (coursier.core.Version(update.currentVersion), coursier.core.Version(update.nextVersion))
    if (current > next) Left(VersionOrderingConflict(update)) else Right(update)
  }

  private def removeBadVersions(update: Update.Single): FilterResult =
    update.newerVersions
      .filterNot(badVersions(update.groupId, update.artifactId))
      .toNel
      .map(versions => update.copy(newerVersions = versions))
      .fold[FilterResult](Left(BadVersions(update)))(Right.apply)

  private def badVersions(groupId: GroupId, artifactId: ArtifactId): String => Boolean =
    (groupId.value, artifactId.name) match {
      case ("com.google.guava", "guava") =>
        List("r03", "r05", "r06", "r07", "r08", "r09").contains
      case ("com.nequissimus", "sort-imports") =>
        List(
          // https://github.com/beautiful-scala/sbt-scalastyle/pull/13
          "36845576"
        ).contains
      case ("com.nequissimus", "sort-imports_2.12") =>
        List(
          // https://github.com/scala-steward-org/scala-steward/issues/1413
          "36845576"
        ).contains
      case ("commons-codec", "commons-codec") =>
        List(
          // https://github.com/scala-steward-org/scala-steward/issues/1753
          "20041127.091804"
        ).contains
      case ("commons-collections", "commons-collections") =>
        List(
          "20030418.083655",
          // https://github.com/albuch/sbt-dependency-check/pull/107
          "20031027.000000",
          // https://github.com/albuch/sbt-dependency-check/pull/85
          "20040102.233541",
          "20040616"
        ).contains
      case ("commons-io", "commons-io") =>
        List(
          // https://github.com/scala-steward-org/scala-steward/issues/1753
          "20030203.000550"
        ).contains
      case ("commons-net", "commons-net") =>
        List(
          // https://github.com/gitbucket/gitbucket/pull/2639
          "20030805.205232",
          "20030623.125255",
          "20030211.160026"
        ).contains
      case ("io.monix", _) =>
        List(
          // https://github.com/scala-steward-org/scala-steward/issues/105
          "3.0.0-fbcb270"
        ).contains
      case ("net.sourceforge.plantuml", "plantuml") =>
        s => {
          val v = Version(s)
          // https://github.com/esamson/remder/pull/5
          (v >= Version("6055") && v <= Version("8059")) ||
          // https://github.com/metabookmarks/sbt-plantuml-plugin/pull/21
          // https://github.com/metabookmarks/sbt-plantuml-plugin/pull/10
          (v >= Version("2017.08") && v <= Version("2017.11"))
        }
      case ("org.http4s", _) =>
        List(
          // https://github.com/http4s/http4s/pull/2153
          "0.19.0"
        ).contains
      case ("org.scala-js", _) =>
        List(
          // https://github.com/scala-js/scala-js/issues/3865
          "0.6.30"
        ).contains
      case _ =>
        _ => false
    }
}
