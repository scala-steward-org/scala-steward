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

package org.scalasteward.core.data

import cats.Order
import cats.implicits.*
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.repoconfig.PullRequestGroup
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import org.scalasteward.core.coursier.VersionsCache.VersionWithFirstSeen

case class ArtifactForUpdate(
    crossDependency: CrossDependency,
    newerGroupId: Option[GroupId] = None,
    newerArtifactId: Option[String] = None
) {
  private val headDependency: Dependency = crossDependency.head
  def groupId: GroupId = headDependency.groupId
  def artifactId: ArtifactId = headDependency.artifactId
  def currentVersion: Version = headDependency.version
}

trait ArtifactUpdateVersions {
  val artifactForUpdate: ArtifactForUpdate

  val refersToUpdateVersions: Nel[Version]

  def show: String
}

/** Captures possible ''candidate'' newer versions that we may update an artifact to.
  *
  * Compare with the other subclass of [[ArtifactUpdateVersions]], [[Update.ForArtifactId]], which
  * denotes the specific ''single'' next version ultimately used in a PR.
  */
case class ArtifactUpdateCandidates(
    artifactForUpdate: ArtifactForUpdate,
    newerVersionsWithFirstSeen: Nel[VersionWithFirstSeen]
) extends ArtifactUpdateVersions {
  override val refersToUpdateVersions: Nel[Version] = newerVersionsWithFirstSeen.map(_.version)

  def asSpecificUpdate(nextVersion: Version): Update.ForArtifactId =
    Update.ForArtifactId(artifactForUpdate, nextVersion)

  override def show: String =
    s"${artifactForUpdate.groupId}:${artifactForUpdate.crossDependency.showArtifactNames} : ${Version.show((artifactForUpdate.currentVersion +: refersToUpdateVersions.toList)*)}"
}

sealed trait Update {

  def on[A](update: Update.Single => A, grouped: Update.Grouped => A): A = this match {
    case g: Update.Grouped => grouped(g)
    case u: Update.Single  => update(u)
  }

  def show: String

  val asSingleUpdates: List[Update.Single]
}

object Update {

  /** Denotes the update of one or more artifacts, which have all matched the same
    * `pullRequests.grouping` config rule.
    *
    * An `Update.Grouped` PR looks like this: [[https://github.com/guardian/etag-caching/pull/62]]
    */
  final case class Grouped(
      name: String,
      title: Option[String],
      updates: List[Update.ForArtifactId]
  ) extends Update {

    override def show: String = name
    override val asSingleUpdates: List[Update.Single] = updates
  }

  sealed trait Single extends Product with Serializable with Update {
    override val asSingleUpdates: List[Update.Single] = List(this)
    def artifactsForUpdate: Nel[ArtifactForUpdate]
    def forArtifactIds: Nel[ForArtifactId]
    def crossDependencies: Nel[CrossDependency]
    def dependencies: Nel[Dependency]
    def groupId: GroupId
    def artifactIds: Nel[ArtifactId]
    def mainArtifactId: String
    def showArtifacts: String
    def groupAndMainArtifactId: (GroupId, String) = (groupId, mainArtifactId)
    def currentVersion: Version
    def nextVersion: Version

    final def name: String = Update.nameOf(groupId, mainArtifactId)

    final override def show: String =
      s"$groupId:$showArtifacts : ${Version.show(currentVersion, nextVersion)}"

    def supersedes(that: Update.Single): Boolean =
      groupAndMainArtifactId == that.groupAndMainArtifactId && nextVersion > that.nextVersion
  }

  /** Denotes the update of a specific single artifact to some particular chosen next version.
    *
    * An update PR with a single `Update.ForArtifactId` looks like this:
    * [[https://github.com/guardian/etag-caching/pull/125]]
    *
    * In the other subclass of [[ArtifactUpdateVersions]], [[ArtifactUpdateCandidates]], _multiple_
    * possible candidate newer versions are stored.
    */
  final case class ForArtifactId(
      artifactForUpdate: ArtifactForUpdate,
      nextVersion: Version
  ) extends Single
      with ArtifactUpdateVersions {
    val crossDependency: CrossDependency = artifactForUpdate.crossDependency

    override val refersToUpdateVersions: Nel[Version] = Nel.one(nextVersion)

    override def artifactsForUpdate: Nel[ArtifactForUpdate] = Nel.one(artifactForUpdate)

    override def showArtifacts: String = crossDependency.showArtifactNames

    lazy val versionUpdate: Version.Update = Version.Update(currentVersion, nextVersion)

    override def forArtifactIds: Nel[ForArtifactId] =
      Nel.one(this)

    override def crossDependencies: Nel[CrossDependency] =
      Nel.one(crossDependency)

    override def dependencies: Nel[Dependency] =
      crossDependency.dependencies

    override def groupId: GroupId =
      artifactForUpdate.groupId

    override def artifactIds: Nel[ArtifactId] =
      dependencies.map(_.artifactId)

    override def mainArtifactId: String =
      artifactId.name

    override def currentVersion: Version =
      artifactForUpdate.currentVersion

    def artifactId: ArtifactId =
      artifactForUpdate.artifactId
  }

  /** Denotes the update of several artifacts which all have the same Maven group-id, and also are
    * all are being updated ''from'' the same version, and updated ''to'' the same `nextVersion`.
    *
    * An `Update.ForGroupId` PR looks like this:
    * [[https://github.com/guardian/etag-caching/pull/128]]
    */
  final case class ForGroupId(
      artifactsForUpdate: Nel[ArtifactForUpdate],
      nextVersion: Version
  ) extends Single {

    override def forArtifactIds: Nel[ForArtifactId] =
      artifactsForUpdate.map(ForArtifactId(_, nextVersion))

    override def crossDependencies: Nel[CrossDependency] =
      artifactsForUpdate.map(_.crossDependency)

    override def dependencies: Nel[Dependency] =
      crossDependencies.flatMap(_.dependencies)

    override def groupId: GroupId =
      dependencies.head.groupId

    override def artifactIds: Nel[ArtifactId] =
      dependencies.map(_.artifactId)

    override def mainArtifactId: String = {
      val possibleMainArtifactIds = for {
        prefix <- artifactIdsPrefix.toList
        suffix <- commonSuffixes
      } yield prefix + suffix

      artifactIds
        .map(_.name)
        .find(possibleMainArtifactIds.contains)
        .getOrElse(artifactIds.head.name)
    }

    override def showArtifacts: String =
      crossDependencies.map(_.showArtifactNames).mkString_("{", ", ", "}")

    override def currentVersion: Version =
      dependencies.head.version

    def artifactIdsPrefix: Option[String] =
      util.string.longestCommonPrefixGteq(artifactIds.map(_.name), 3)
  }

  val commonSuffixes: List[String] =
    List("config", "contrib", "core", "extra", "server")

  def nameOf(groupId: GroupId, artifactId: String): String =
    if (commonSuffixes.contains(artifactId))
      util.string.rightmostLabel(groupId.value)
    else
      artifactId

  def groupByArtifactIdName(updates: List[ForArtifactId]): List[ForArtifactId] = {
    val groups0 =
      updates.groupByNel(s => (s.groupId, s.artifactId.name, s.currentVersion, s.nextVersion))
    val groups1 = groups0.values.map { group =>
      val dependencies = group.flatMap(_.dependencies).distinct.sorted
      val update: Update.ForArtifactId = group.head
      update.copy(artifactForUpdate =
        update.artifactForUpdate.copy(crossDependency = CrossDependency(dependencies))
      )
    }
    groups1.toList.distinct.sortBy(u => u: Update.Single)
  }

  def groupByGroupId(updates: List[ForArtifactId]): List[Single] = {
    val groups0 =
      updates.groupByNel(s => (s.groupId, s.versionUpdate))
    val groups1 = groups0.map { case ((_, versionUpdate), group) =>
      if (group.tail.isEmpty) group.head
      else ForGroupId(group.map(_.artifactForUpdate), versionUpdate.nextVersion)
    }
    groups1.toList.distinct.sorted
  }

  /** Processes the provided updates using the group configuration. Each update will only be present
    * in the first group it falls into.
    *
    * Updates that do not fall into any group will be returned back in the second return parameter.
    */
  def groupByPullRequestGroup(
      groups: List[PullRequestGroup],
      updates: List[Update.ForArtifactId]
  ): (List[Grouped], List[Update.ForArtifactId]) =
    groups.foldLeft((List.empty[Grouped], updates)) { case ((grouped, notGrouped), group) =>
      notGrouped.partition(group.matches) match {
        case (Nil, rest)     => (grouped, rest)
        case (matched, rest) => (grouped :+ Grouped(group.name, group.title, matched), rest)
      }
    }

  implicit val SingleOrder: Order[Single] =
    Order.by((u: Single) => (u.crossDependencies, u.nextVersion))

  // Encoder and Decoder instances

  // ForArtifactId

  implicit private val forArtifactIdEncoder: Encoder[ForArtifactId] =
    Encoder.forProduct1("ForArtifactId")(identity[ForArtifactId]) {
      Encoder.forProduct4("crossDependency", "newerVersions", "newerGroupId", "newerArtifactId") {
        s =>
          (
            s.crossDependency,
            Seq(s.nextVersion),
            s.artifactForUpdate.newerGroupId,
            s.artifactForUpdate.newerArtifactId
          )
      }
    }

  private val unwrappedForArtifactIdDecoder: Decoder[ForArtifactId] =
    Decoder.forProduct4("crossDependency", "newerVersions", "newerGroupId", "newerArtifactId") {
      (
          crossDependency: CrossDependency,
          newerVersions: List[Version],
          newerGroupId: Option[GroupId],
          newerArtifactId: Option[String]
      ) =>
        ForArtifactId(
          ArtifactForUpdate(crossDependency, newerGroupId, newerArtifactId),
          newerVersions.head
        )
    }

  private val forArtifactIdDecoderV2 =
    Decoder.forProduct1("ForArtifactId")(identity[ForArtifactId])(unwrappedForArtifactIdDecoder)

  implicit private val forArtifactIdDecoder: Decoder[ForArtifactId] =
    forArtifactIdDecoderV2

  // ForGroupId

  private val forGroupIdEncoder: Encoder[ForGroupId] =
    Encoder.forProduct1("ForGroupId")(identity[ForGroupId]) {
      Encoder.forProduct1("forArtifactIds")(_.forArtifactIds)
    }

  private val unwrappedForGroupIdDecoderV3: Decoder[ForGroupId] =
    Decoder.forProduct1("forArtifactIds") { (forArtifactIds: Nel[ForArtifactId]) =>
      ForGroupId(forArtifactIds.map(_.artifactForUpdate), forArtifactIds.head.nextVersion)
    }

  private val forGroupIdDecoderV3: Decoder[ForGroupId] =
    Decoder.forProduct1("ForGroupId")(identity[ForGroupId])(unwrappedForGroupIdDecoderV3)

  private val forGroupIdDecoder: Decoder[ForGroupId] = forGroupIdDecoderV3

  // Grouped

  private val groupedEncoder: Encoder[Grouped] =
    Encoder.forProduct1("Grouped")(identity[Grouped]) {
      Encoder.forProduct3("name", "title", "updates")(s => (s.name, s.title, s.updates))
    }

  private val groupedDecoder: Decoder[Grouped] =
    Decoder.forProduct1("Grouped")(identity[Grouped]) {
      Decoder.forProduct3("name", "title", "updates") {
        (name: String, title: Option[String], updates: List[ForArtifactId]) =>
          Grouped(name, title, updates)
      }
    }

  // Update

  implicit val updateEncoder: Encoder[Update] = {
    case update: ForArtifactId => forArtifactIdEncoder.apply(update)
    case update: ForGroupId    => forGroupIdEncoder.apply(update)
    case update: Grouped       => groupedEncoder.apply(update)
  }

  implicit val updateDecoder: Decoder[Update] =
    forArtifactIdDecoder
      .widen[Update]
      .or(forGroupIdDecoder.widen[Update])
      .or(groupedDecoder.widen[Update])
}
