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

package org.scalasteward.core.update

import cats.Traverse
import cats.syntax.all.*
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

object show {
  def oneLiner(update: Update.Single): String =
    commaSeparated(update.groupId, update.crossDependencies.map(_.head.artifactId.name))

  private def commaSeparated(groupId: GroupId, artifactIds: Nel[String]): String = {
    val artifacts = showArtifacts(groupId, artifactIds).toList
    val count = maxArtifacts(artifacts)
    val items = if (count < artifacts.size) artifacts.take(count) :+ "..." else artifacts
    items.mkString(", ")
  }

  private def maxArtifacts(artifacts: List[String]): Int = {
    val maxLength = 32
    val accumulatedLengths = artifacts.map(_.length).scanLeft(0)(_ + _).drop(1)
    math.max(1, accumulatedLengths.takeWhile(_ <= maxLength).size)
  }

  private def showArtifacts[F[_]: Traverse](groupId: GroupId, artifactIds: F[String]): F[String] = {
    val includeGroupId = util.intersects(artifactIds, Update.commonSuffixes)
    artifactIds.map(showArtifact(groupId, _, includeGroupId))
  }

  private def showArtifact(
      groupId: GroupId,
      artifactId: String,
      includeGroupId: Boolean
  ): String = {
    val groupName = util.string.rightmostLabel(groupId.value)
    if (!includeGroupId || artifactId.contains(groupName)) artifactId
    else groupName + ":" + artifactId
  }
}
