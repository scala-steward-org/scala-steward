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
import io.circe.{Codec, Decoder, Encoder}
import org.scalasteward.core.repoconfig.PullRequestGroup
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

sealed trait VersionData {
  val refersToVersions: Nel[Version]
}

case class NextVersion(nextVersion: Version) extends VersionData {
  override val refersToVersions: Nel[Version] = Nel.one(nextVersion)

  /**
   * Only useful for invoking UpdatePattern.findMatch()?
   */
  lazy val asNewerVersions: NewerVersions = NewerVersions(refersToVersions)
}

case class NewerVersions(newerVersions: Nel[Version]) extends VersionData {
  override val refersToVersions: Nel[Version] = refersToVersions

  lazy val toNextVersion: NextVersion = NextVersion(newerVersions.head) // TODO how does this compare to org.scalasteward.core.update.FilterAlg.selectSuitableNextVersion ?
}

sealed trait Update[VData <: VersionData] {

  def on[A](update: Update.Single[VData] => A, grouped: Update.Grouped[VData] => A): A = this match {
    case g: Update.Grouped[VData] => grouped(g)
    case u: Update.Single[VData]  => update(u)
  }

  def show: String

  val asSingleUpdates: List[Update.Single[VData]]
}

object Update {

  final case class Grouped[VData <: VersionData](
      name: String,
      title: Option[String],
      updates: List[Update.ForArtifactId[VData]]
  ) extends Update[VData] {

    override def show: String = name
    override val asSingleUpdates: List[Update.Single[VData]] = updates
  }

  sealed trait Single[VData <: VersionData] extends Product with Serializable with Update[VData] {
    override val asSingleUpdates: List[Update.Single[VData]] = List(this)
    def forArtifactIds: Nel[ForArtifactId[VData]]
    def crossDependencies: Nel[CrossDependency]
    def dependencies: Nel[Dependency]
    def groupId: GroupId
    def artifactIds: Nel[ArtifactId]
    def mainArtifactId: String
    def groupAndMainArtifactId: (GroupId, String) = (groupId, mainArtifactId)
    def currentVersion: Version
    def versionData: VData

    final def name: String = Update.nameOf(groupId, mainArtifactId)

    final override def show: String = {
      val artifacts = this match {
        case s: ForArtifactId[VData] => s.crossDependency.showArtifactNames
        case g: ForGroupId[VData] => g.crossDependencies.map(_.showArtifactNames).mkString_("{", ", ", "}")
      }
      val versions = {
        val vs0 = (currentVersion :: versionData.refersToVersions).toList.map(_.value)
        val vs1: Seq[String] = if (vs0.size > 6) vs0.take(3) ++ ("..." :: vs0.takeRight(3)) else vs0
        vs1.mkString(" -> ")
      }
      s"$groupId:$artifacts : $versions"
    }

    def withVersionData(versionData: VData): Update.Single[VData] = this match {
      case s @ ForArtifactId(_, _, _, _) =>
        s.copy(versionData = versionData)
      case ForGroupId(forArtifactIds) =>
        ForGroupId(forArtifactIds.map(_.copy(versionData = versionData)))
    }
  }

  final case class ForArtifactId[VData <: VersionData](
      crossDependency: CrossDependency,
      versionData: VData,
      newerGroupId: Option[GroupId] = None,
      newerArtifactId: Option[String] = None
  ) extends Single[VData] {
    def transformVersionData[VData2 <: VersionData](f: VData => VData2): ForArtifactId[VData2] =
      copy(versionData = f(versionData))

    override def forArtifactIds: Nel[ForArtifactId[VData]] =
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

  final case class ForGroupId[VData <: VersionData](
      forArtifactIds: Nel[ForArtifactId[VData]]
  ) extends Single[VData] {

    override def versionData: VData = forArtifactIds.head.versionData

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
      } yield prefix + suffix

      artifactIds
        .map(_.name)
        .find(possibleMainArtifactIds.contains)
        .getOrElse(artifactIds.head.name)
    }

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

  def groupByArtifactIdName(updates: List[ForArtifactId[NextVersion]]): List[ForArtifactId[NextVersion]] = {
    val groups0 =
      updates.groupByNel(s => (s.groupId, s.artifactId.name, s.currentVersion, s.versionData.nextVersion))
    val groups1 = groups0.values.map { group =>
      val dependencies = group.flatMap(_.crossDependency.dependencies).distinct.sorted
      group.head.copy(crossDependency = CrossDependency(dependencies))
    }
    groups1.toList.distinct.sortBy(u => u: Update.Single[NextVersion])
  }

  def groupByGroupId(updates: List[ForArtifactId[NextVersion]]): List[Single[NextVersion]] = {
    val groups0 =
      updates.groupByNel(s => (s.groupId, s.currentVersion, s.versionData.nextVersion))
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
      updates: List[Update.ForArtifactId[NextVersion]]
  ): (List[Grouped[NextVersion]], List[Update.ForArtifactId[NextVersion]]) =
    groups.foldLeft((List.empty[Grouped[NextVersion]], updates)) { case ((grouped, notGrouped), group) =>
      notGrouped.partition(group.matches) match {
        case (Nil, rest)     => (grouped, rest)
        case (matched, rest) => (grouped :+ Grouped(group.name, group.title, matched), rest)
      }
    }

  implicit val SingleOrder: Order[Single[NextVersion]] =
    Order.by((u: Single[NextVersion]) => (u.crossDependencies, u.versionData.nextVersion))

  // Encoder and Decoder instances

  implicit private val forNextVersionEncoder: Codec[NextVersion] =
    Codec.forProduct1[NextVersion, Version]("NextVersion")(NextVersion(_))(_.nextVersion)

  // ForArtifactId

  implicit private val forArtifactIdEncoder: Encoder[ForArtifactId[NextVersion]] =
    Encoder.forProduct1("ForArtifactId")(identity[ForArtifactId[NextVersion]]) {
      Encoder.forProduct4("crossDependency", "newerVersions", "newerGroupId", "newerArtifactId") {
        s => (s.crossDependency, s.versionData, s.newerGroupId, s.newerArtifactId)
      }
    }

  private val unwrappedForArtifactIdDecoder: Decoder[ForArtifactId[NextVersion]] =
    Decoder.forProduct4("crossDependency", "nextVersion", "newerGroupId", "newerArtifactId") {
      (
          crossDependency: CrossDependency,
          nextVersion: NextVersion,
          newerGroupId: Option[GroupId],
          newerArtifactId: Option[String]
      ) =>
        ForArtifactId(crossDependency, nextVersion, newerGroupId, newerArtifactId)
    }

  private val forArtifactIdDecoderV2 =
    Decoder.forProduct1("ForArtifactId")(identity[ForArtifactId[NextVersion]])(unwrappedForArtifactIdDecoder)

  implicit private val forArtifactIdDecoder: Decoder[ForArtifactId[NextVersion]] =
    forArtifactIdDecoderV2

  // ForGroupId

  private val forGroupIdEncoder: Encoder[ForGroupId[NextVersion]] =
    Encoder.forProduct1("ForGroupId")(identity[ForGroupId[NextVersion]]) {
      Encoder.forProduct1("forArtifactIds")(_.forArtifactIds)
    }

  private val unwrappedForGroupIdDecoderV3: Decoder[ForGroupId[NextVersion]] =
    Decoder.forProduct1("forArtifactIds") { (forArtifactIds: Nel[ForArtifactId[NextVersion]]) =>
      ForGroupId(forArtifactIds)
    }

  private val forGroupIdDecoderV3: Decoder[ForGroupId[NextVersion]] =
    Decoder.forProduct1("ForGroupId")(identity[ForGroupId[NextVersion]])(unwrappedForGroupIdDecoderV3)

  private val forGroupIdDecoder: Decoder[ForGroupId[NextVersion]] = forGroupIdDecoderV3

  // Grouped

  private val groupedEncoder: Encoder[Grouped[NextVersion]] =
    Encoder.forProduct1("Grouped")(identity[Grouped[NextVersion]]) {
      Encoder.forProduct3("name", "title", "updates")(s => (s.name, s.title, s.updates))
    }

  private val groupedDecoder: Decoder[Grouped[NextVersion]] =
    Decoder.forProduct1("Grouped")(identity[Grouped[NextVersion]]) {
      Decoder.forProduct3("name", "title", "updates") {
        (name: String, title: Option[String], updates: List[ForArtifactId[NextVersion]]) =>
          Grouped(name, title, updates)
      }
    }

  // Update

  implicit val updateEncoder: Encoder[Update[NextVersion]] = {
    case update: ForArtifactId[NextVersion] => forArtifactIdEncoder.apply(update)
    case update: ForGroupId[NextVersion]    => forGroupIdEncoder.apply(update)
    case update: Grouped[NextVersion]       => groupedEncoder.apply(update)
  }

  implicit val updateDecoder: Decoder[Update[NextVersion]] =
    forArtifactIdDecoder
      .widen[Update[NextVersion]]
      .or(forGroupIdDecoder.widen[Update[NextVersion]])
      .or(groupedDecoder.widen[Update[NextVersion]])
}
