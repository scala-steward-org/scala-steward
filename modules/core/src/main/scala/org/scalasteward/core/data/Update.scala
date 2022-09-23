/*
 * Copyright 2018-2022 Scala Steward contributors
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
import io.circe.Codec
import io.circe.generic.semiauto._
import org.scalasteward.core.data.Update.Group
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.repoconfig.PullRequestGroup
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import org.scalasteward.core.util.string.MinLengthString

import scala.annotation.nowarn

sealed trait AnUpdate {


  def show: String

}

final case class GroupedUpdate(name: String, title: Option[String], updates: List[Update])
    extends AnUpdate {

  override def show: String = name

}

object GroupedUpdate {

  /**
    * Processes the provided updates using the group configuration. Each update will only be present in the
    * first group it falls into.
    *
    * Updates that do not fall into any group will be returned back in the second return parameter.
    */
  @nowarn
  def from(
      groups: List[PullRequestGroup],
      updates: List[Update.Single]
  ): (List[GroupedUpdate], List[Update.Single]) = (Nil, updates)

}

sealed trait Update extends Product with Serializable with AnUpdate {
  def crossDependencies: Nel[CrossDependency]
  def dependencies: Nel[Dependency]
  def groupId: GroupId
  def artifactIds: Nel[ArtifactId]
  def mainArtifactId: String
  def currentVersion: Version
  def newerVersions: Nel[Version]

  final def name: String =
    Update.nameOf(groupId, mainArtifactId)

  final def nextVersion: Version =
    newerVersions.head

  final override def show: String = {
    val artifacts = this match {
      case s: Single => s.crossDependency.showArtifactNames
      case g: Group  => g.crossDependencies.map(_.showArtifactNames).mkString_("{", ", ", "}")
    }
    val versions = {
      val vs0 = (currentVersion :: newerVersions).toList
      val vs1 = if (vs0.size > 6) vs0.take(3) ++ ("..." :: vs0.takeRight(3)) else vs0
      vs1.mkString("", " -> ", "")
    }
    s"$groupId:$artifacts : $versions"
  }

  def withNewerVersions(versions: Nel[Version]): Update = this match {
    case s @ Single(_, _, _, _) =>
      s.copy(newerVersions = versions)
    case g @ Group(_, _) =>
      g.copy(newerVersions = versions)
  }
}

object Update {
  final case class Single(
      crossDependency: CrossDependency,
      newerVersions: Nel[Version],
      newerGroupId: Option[GroupId] = None,
      newerArtifactId: Option[String] = None
  ) extends Update {
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

  final case class Group(
      crossDependencies: Nel[CrossDependency],
      newerVersions: Nel[Version]
  ) extends Update {
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

  def groupByArtifactIdName(updates: List[Single]): List[Single] = {
    val groups0 =
      updates.groupByNel(s => (s.groupId, s.artifactId.name, s.currentVersion, s.newerVersions))
    val groups1 = groups0.values.map { group =>
      val dependencies = group.flatMap(_.crossDependency.dependencies).distinct.sorted
      group.head.copy(crossDependency = CrossDependency(dependencies))
    }
    groups1.toList.distinct.sortBy(u => u: Update)
  }

  def groupByGroupId(updates: List[Single]): List[Update] = {
    val groups0 =
      updates.groupByNel(s => (s.groupId, s.currentVersion, s.newerVersions))
    val groups1 = groups0.values.map { group =>
      if (group.tail.isEmpty) group.head
      else Group(group.map(_.crossDependency), group.head.newerVersions)
    }
    groups1.toList.distinct.sorted
  }

  implicit val updateCodec: Codec[Update] =
    deriveCodec

  implicit val updateOrder: Order[Update] =
    Order.by((u: Update) => (u.crossDependencies, u.newerVersions))
}
