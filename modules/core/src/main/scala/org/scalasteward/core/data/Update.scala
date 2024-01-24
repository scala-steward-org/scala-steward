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

package org.scalasteward.core.data

import cats.Order
import cats.implicits._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.scalasteward.core.repoconfig.PullRequestGroup
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import org.scalasteward.core.util.string.MinLengthString

sealed trait Update {

  def on[A](update: Update.Single => A, grouped: Update.Grouped => A): A = this match {
    case g: Update.Grouped => grouped(g)
    case u: Update.Single  => update(u)
  }

  def show: String

  val asSingleUpdates: List[Update.Single]
}

object Update {

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
    def forArtifactIds: Nel[ForArtifactId]
    def crossDependencies: Nel[CrossDependency]
    def dependencies: Nel[Dependency]
    def groupId: GroupId
    def artifactIds: Nel[ArtifactId]
    def mainArtifactId: String
    def groupAndMainArtifactId: (GroupId, String) = (groupId, mainArtifactId)
    def currentVersion: Version
    def newerVersions: Nel[Version]

    final def name: String = Update.nameOf(groupId, mainArtifactId)

    final def nextVersion: Version = newerVersions.head

    final override def show: String = {
      val artifacts = this match {
        case s: ForArtifactId => s.crossDependency.showArtifactNames
        case g: ForGroupId => g.crossDependencies.map(_.showArtifactNames).mkString_("{", ", ", "}")
      }
      val versions = {
        val vs0 = (currentVersion :: newerVersions).toList
        val vs1 = if (vs0.size > 6) vs0.take(3) ++ ("..." :: vs0.takeRight(3)) else vs0
        vs1.mkString("", " -> ", "")
      }
      s"$groupId:$artifacts : $versions"
    }

    def withNewerVersions(versions: Nel[Version]): Update.Single = this match {
      case s @ ForArtifactId(_, _, _, _) =>
        s.copy(newerVersions = versions)
      case ForGroupId(forArtifactIds) =>
        ForGroupId(forArtifactIds.map(_.copy(newerVersions = versions)))
    }
  }

  final case class ForArtifactId(
      crossDependency: CrossDependency,
      newerVersions: Nel[Version],
      newerGroupId: Option[GroupId] = None,
      newerArtifactId: Option[String] = None
  ) extends Single {
    override def forArtifactIds: Nel[ForArtifactId] =
      Nel.one(this)

    override def crossDependencies: Nel[CrossDependency] =
      Nel.one(crossDependency)

    override def dependencies: Nel[Dependency] =
      crossDependency.dependencies

    override def groupId: GroupId =
      crossDependency.head.groupId

    override def artifactIds: Nel[ArtifactId] =
      dependencies.map(_.artifactId)

    override def mainArtifactId: String =
      artifactId.name

    override def currentVersion: Version =
      crossDependency.head.version

    def artifactId: ArtifactId =
      crossDependency.head.artifactId
  }

  final case class ForGroupId(
      forArtifactIds: Nel[ForArtifactId]
  ) extends Single {
    override def crossDependencies: Nel[CrossDependency] =
      forArtifactIds.map(_.crossDependency)

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
      } yield prefix.value + suffix

      artifactIds
        .map(_.name)
        .find(possibleMainArtifactIds.contains)
        .getOrElse(artifactIds.head.name)
    }

    override def currentVersion: Version =
      dependencies.head.version

    override def newerVersions: Nel[Version] =
      forArtifactIds.head.newerVersions

    def artifactIdsPrefix: Option[MinLengthString[3]] =
      util.string.longestCommonPrefixGreater[3](artifactIds.map(_.name))
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
      updates.groupByNel(s => (s.groupId, s.artifactId.name, s.currentVersion, s.newerVersions))
    val groups1 = groups0.values.map { group =>
      val dependencies = group.flatMap(_.crossDependency.dependencies).distinct.sorted
      group.head.copy(crossDependency = CrossDependency(dependencies))
    }
    groups1.toList.distinct.sortBy(u => u: Update.Single)
  }

  def groupByGroupId(updates: List[ForArtifactId]): List[Single] = {
    val groups0 =
      updates.groupByNel(s => (s.groupId, s.currentVersion, s.newerVersions))
    val groups1 = groups0.values.map { group =>
      if (group.tail.isEmpty) group.head else ForGroupId(group)
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

  // TODO: Derive all instances of `Encoder`/`Decoder` here using `deriveCodec`
  // Partially manually implemented so we don't fail reading old caches (those
  // still using `Single`/`Group`)

  implicit val ForArtifactIdEncoder: Encoder[ForArtifactId] = {
    val derived = deriveEncoder[ForArtifactId]

    derived.mapJson(json => Json.obj("ForArtifactId" -> json))
  }

  implicit val ForArtifactIdDecoder: Decoder[ForArtifactId] = {
    val derived = deriveDecoder[ForArtifactId]
    derived
      .prepare(_.downField("ForArtifactId"))
      .or(derived.prepare(_.downField("Single")))
  }

  implicit val ForGroupIdEncoder: Encoder[ForGroupId] = {
    val derived = deriveEncoder[ForGroupId]

    derived.mapJson(json => Json.obj("ForGroupId" -> json))
  }

  private val oldForGroupIdDecoder: Decoder[ForGroupId] =
    (c: HCursor) =>
      for {
        crossDependencies <- c.downField("crossDependencies").as[Nel[CrossDependency]]
        newerVersions <- c.downField("newerVersions").as[Nel[Version]]
        forArtifactIds = crossDependencies
          .map(crossDependency => ForArtifactId(crossDependency, newerVersions))
      } yield ForGroupId(forArtifactIds)

  implicit val ForGroupIdDecoder: Decoder[ForGroupId] =
    deriveDecoder[ForGroupId]
      .prepare(_.downField("ForGroupId"))
      .or(oldForGroupIdDecoder.prepare(_.downField("ForGroupId")))
      .or(oldForGroupIdDecoder.prepare(_.downField("Group")))

  implicit val GroupedEncoder: Encoder[Grouped] = {
    val derived = deriveEncoder[Grouped]

    derived.mapJson(json => Json.obj("Grouped" -> json))
  }

  implicit val GroupedDecoder: Decoder[Grouped] =
    deriveDecoder[Grouped].prepare(_.downField("Grouped"))

  implicit val SingleOrder: Order[Single] =
    Order.by((u: Single) => (u.crossDependencies, u.newerVersions))

  implicit val UpdateEncoder: Encoder[Update] = {
    case update: Grouped       => update.asJson
    case update: ForArtifactId => update.asJson
    case update: ForGroupId    => update.asJson
  }

  implicit val UpdateDecoder: Decoder[Update] =
    ForArtifactIdDecoder
      .widen[Update]
      .or(ForGroupIdDecoder.widen[Update])
      .or(Decoder[Grouped].widen[Update])

}
