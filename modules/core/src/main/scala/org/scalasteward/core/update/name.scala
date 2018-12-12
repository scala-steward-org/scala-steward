/*
 * Copyright 2018 scala-steward contributors
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
import org.scalasteward.core.model.Update
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

object name {
  def oneLiner(update: Update): String =
    commaSeparated(update.groupId, update.artifactIds)

  private def commaSeparated(groupId: String, artifactIds: Nel[String]): String = {
    val names = artifactNames(groupId, artifactIds).toList
    val count = maxNames(names)
    val end = if (names.size > count) ", ..." else ""
    names.take(count).mkString("", ", ", end)
  }

  private def maxNames(names: List[String]): Int = {
    val maxLength = 32
    val accumulatedLengths = names.map(_.length).scanLeft(0)(_ + _).tail
    math.max(1, accumulatedLengths.takeWhile(_ <= maxLength).size)
  }

  private def artifactNames(groupId: String, artifactIds: Nel[String]): Nel[String] = {
    val includeGroupId = util.intersects(artifactIds, Update.commonSuffixes)
    artifactIds.map(artifactName(groupId, _, includeGroupId))
  }

  private def artifactName(groupId: String, artifactId: String, includeGroupId: Boolean): String = {
    val groupName = groupId.split('.').lastOption.getOrElse(groupId)
    if (!includeGroupId || artifactId.contains(groupName)) artifactId
    else groupName + ":" + artifactId
  }
}
