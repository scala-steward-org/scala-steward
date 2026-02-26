/*
 * Copyright 2018-2025 Scala Steward contributors
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

package org.scalasteward.core.repoconfig

import cats.implicits.*
import cats.{Eq, Monoid}
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe.generic.semiauto.deriveCodec
import io.circe.refined.*
import io.circe.{Codec, Decoder}
import org.scalasteward.core.buildtool.{gradle, maven, mill, sbt}
import org.scalasteward.core.data.{ArtifactUpdateCandidates, GroupId}
import org.scalasteward.core.scalafmt
import org.scalasteward.core.update.FilterAlg.*
import org.scalasteward.core.util.{combineOptions, intellijThisImportIsUsed, Timestamp}

final case class UpdatesConfig(
    private val pin: Option[List[UpdatePattern]] = None,
    private val allow: Option[List[UpdatePattern]] = None,
    private val allowPreReleases: Option[List[UpdatePattern]] = None,
    private val ignore: Option[List[UpdatePattern]] = None,
    private val retracted: Option[List[RetractedArtifact]] = None,
    limit: Option[NonNegInt] = UpdatesConfig.defaultLimit,
    private val fileExtensions: Option[List[String]] = None,
    private val cooldown: Option[CooldownConfig] = None
) {
  private[repoconfig] def pinOrDefault: List[UpdatePattern] =
    pin.getOrElse(Nil)

  private def allowOrDefault: List[UpdatePattern] =
    allow.getOrElse(Nil)

  def allowPreReleasesOrDefault: List[UpdatePattern] =
    allowPreReleases.getOrElse(Nil)

  private def ignoreOrDefault: List[UpdatePattern] =
    ignore.getOrElse(Nil)

  def retractedOrDefault: List[RetractedArtifact] =
    retracted.getOrElse(Nil)

  def fileExtensionsOrDefault: Set[String] =
    fileExtensions.fold(UpdatesConfig.defaultFileExtensions)(_.toSet)

  def keep(update: ArtifactUpdateCandidates, currentTime: Timestamp): FilterResult =
    isAllowed(update).flatMap(isPinned).flatMap(isIgnored).flatMap(isTooRecent(_, currentTime))

  def preRelease(update: ArtifactUpdateCandidates): FilterResult =
    isAllowedPreReleases(update)

  private def isAllowedPreReleases(update: ArtifactUpdateCandidates): FilterResult = {
    val m = UpdatePattern.findMatch(allowPreReleasesOrDefault, update, include = true)
    if (m.filteredVersions.nonEmpty)
      Right(update)
    else Left(NotAllowedByConfig(update))
  }

  private def isAllowed(update: ArtifactUpdateCandidates): FilterResult =
    if (allowOrDefault.isEmpty) Right(update)
    else {
      val m = UpdatePattern.findMatch(allowOrDefault, update, include = true)
      update.filterVersions(m.filteredVersions.contains).toRight(NotAllowedByConfig(update))
    }

  private def isPinned(update: ArtifactUpdateCandidates): FilterResult = {
    val m = UpdatePattern.findMatch(pinOrDefault, update, include = true)
    if (m.byArtifactId.isEmpty) Right(update)
    else update.filterVersions(m.filteredVersions.contains).toRight(VersionPinnedByConfig(update))
  }

  private def isIgnored(update: ArtifactUpdateCandidates): FilterResult = {
    val m = UpdatePattern.findMatch(ignoreOrDefault, update, include = false)
    update.filterVersions(m.filteredVersions.contains).toRight(IgnoredByConfig(update))
  }

  private def isTooRecent(update: ArtifactUpdateCandidates, currentTime: Timestamp): FilterResult =
    cooldown
      .map(_.filterForAge(update, currentTime).toRight(TooRecentForCooldown(update)))
      .getOrElse(Right(update))
}

object UpdatesConfig {
  val defaultFileExtensions: Set[String] =
    Set(
      ".mill",
      ".sbt",
      ".sbt.shared",
      ".sc",
      ".scala",
      ".sdkmanrc",
      ".yml",
      "mise.toml",
      "mise/config.toml",
      gradle.libsVersionsTomlName,
      maven.pomXmlName,
      mill.MillAlg.millVersionName,
      sbt.buildPropertiesName,
      scalafmt.scalafmtConfName
    )

  val defaultLimit: Option[NonNegInt] = None

  implicit val updatesConfigEq: Eq[UpdatesConfig] =
    Eq.fromUniversalEquals

  implicit val updatesConfigCodec: Codec[UpdatesConfig] =
    deriveCodec

  implicit val updatesConfigMonoid: Monoid[UpdatesConfig] =
    Monoid.instance(
      UpdatesConfig(),
      (x, y) =>
        UpdatesConfig(
          pin = mergePin(x.pin, y.pin),
          allow = mergeAllow(x.allow, y.allow),
          allowPreReleases = mergeAllow(x.allowPreReleases, y.allowPreReleases),
          ignore = mergeIgnore(x.ignore, y.ignore),
          retracted = x.retracted |+| y.retracted,
          limit = x.limit.orElse(y.limit),
          fileExtensions = mergeFileExtensions(x.fileExtensions, y.fileExtensions),
          cooldown = mergeCooldown(x.cooldown, y.cooldown)
        )
    )

  //  Strategy: union with repo preference in terms of revision
  private[repoconfig] def mergePin(
      x: Option[List[UpdatePattern]],
      y: Option[List[UpdatePattern]]
  ): Option[List[UpdatePattern]] = combineOptions(x, y) { (x, y) =>
    x.filterNot { p1 =>
      y.exists(p2 => p1.groupId === p2.groupId && p1.artifactId === p2.artifactId)
    } ::: y
  }

  private[repoconfig] val nonExistingUpdatePattern: List[UpdatePattern] =
    List(UpdatePattern(GroupId("non-exist"), None, None))

  //  Strategy: superset
  //  Xa.Ya.Za |+| Xb.Yb.Zb
  private[repoconfig] def mergeAllow(
      x: Option[List[UpdatePattern]],
      y: Option[List[UpdatePattern]]
  ): Option[List[UpdatePattern]] = combineOptions(x, y) { (x, y) =>
    (x, y) match {
      case (Nil, second) => second
      case (first, Nil)  => first
      case _             =>
        //  remove duplicates first by calling .distinct
        val xm: Map[GroupId, List[UpdatePattern]] = x.distinct.groupBy(_.groupId)
        val ym: Map[GroupId, List[UpdatePattern]] = y.distinct.groupBy(_.groupId)
        val builder = new collection.mutable.ListBuffer[UpdatePattern]()

        //  first of all, we only allow intersection (superset)
        val keys = xm.keySet.intersect(ym.keySet)

        keys.foreach { groupId =>
          builder ++= mergeAllowGroupId(xm(groupId), ym(groupId))
        }

        if (builder.isEmpty) nonExistingUpdatePattern
        else builder.distinct.toList
    }
  }

  //  merge UpdatePattern for same group id
  private def mergeAllowGroupId(
      x: List[UpdatePattern],
      y: List[UpdatePattern]
  ): List[UpdatePattern] =
    (x.exists(_.isWholeGroupIdAllowed), y.exists(_.isWholeGroupIdAllowed)) match {
      case (true, _) => y
      case (_, true) => x
      case _         =>
        //  case with concrete artifacts / versions
        val builder = new collection.mutable.ListBuffer[UpdatePattern]()
        val xByArtifacts = x.groupBy(_.artifactId)
        val yByArtifacts = y.groupBy(_.artifactId)

        x.foreach { updatePattern =>
          if (satisfyUpdatePattern(updatePattern, yByArtifacts))
            builder += updatePattern
        }
        y.foreach { updatePattern =>
          if (satisfyUpdatePattern(updatePattern, xByArtifacts))
            builder += updatePattern
        }

        if (builder.isEmpty) nonExistingUpdatePattern
        else builder.toList
    }

  private def satisfyUpdatePattern(
      targetUpdatePattern: UpdatePattern,
      comparedUpdatePatternsByArtifact: Map[Option[String], List[UpdatePattern]]
  ): Boolean =
    comparedUpdatePatternsByArtifact.get(targetUpdatePattern.artifactId).exists { matchedVersions =>
      //  For simplicity I'm using direct equals here between versions. Feel free to make it more advanced
      matchedVersions.exists(up => up.version.isEmpty || up.version === targetUpdatePattern.version)
    }

  //  Strategy: union
  private[repoconfig] def mergeIgnore(
      x: Option[List[UpdatePattern]],
      y: Option[List[UpdatePattern]]
  ): Option[List[UpdatePattern]] = combineOptions(x, y) { (x, y) =>
    x ::: y.filterNot(x.contains)
  }

  private[repoconfig] def mergeFileExtensions(
      x: Option[List[String]],
      y: Option[List[String]]
  ): Option[List[String]] =
    combineOptions(x, y)(_.intersect(_))

  private[repoconfig] def mergeCooldown(
      x: Option[CooldownConfig],
      y: Option[CooldownConfig]
  ): Option[CooldownConfig] = (y ++ x).headOption // for now, simply let local override global

  intellijThisImportIsUsed(refinedDecoder: Decoder[NonNegInt])
}
