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

import cats.Traverse
import cats.implicits._
import org.scalasteward.core.model._
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

object show {
  def oneLiner(labels: List[Label]): String = labels.map(_.name).mkString(", ")

  def oneLiner(update: Update): String =
    commaSeparated(update.groupId, update.artifactIds)

  private def commaSeparated(groupId: String, artifactIds: Nel[String]): String = {
    val artifacts = showArtifacts(groupId, artifactIds).toList
    val count = maxArtifacts(artifacts)
    val items = if (count < artifacts.size) artifacts.take(count) :+ "..." else artifacts
    items.mkString(", ")
  }

  private def maxArtifacts(artifacts: List[String]): Int = {
    val maxLength = 32
    val accumulatedLengths = artifacts.map(_.length).scanLeft(0)(_ + _).tail
    math.max(1, accumulatedLengths.takeWhile(_ <= maxLength).size)
  }

  private def showArtifacts[F[_]: Traverse](groupId: String, artifactIds: F[String]): F[String] = {
    val includeGroupId = util.intersects(artifactIds, Update.commonSuffixes)
    artifactIds.map(showArtifact(groupId, _, includeGroupId))
  }

  private def showArtifact(groupId: String, artifactId: String, includeGroupId: Boolean): String = {
    val groupName = util.string.rightmostLabel(groupId)
    if (!includeGroupId || artifactId.contains(groupName)) artifactId
    else groupName + ":" + artifactId
  }
}
