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

package org.scalasteward.core

import org.scalasteward.core.model.Update
import org.scalasteward.core.util.Nel

object NameResolver {

  def resolve(update: Update): String =
    group(update.groupId, update.artifactIds)

  private def group(groupId: String, artifactIds: Nel[String]): String = {
    val includeGroupId = artifactIds.exists(Update.commonSuffixes.contains)
    val artifactNames = artifactIds.map(single(groupId, _, includeGroupId))
    val maxArtifacts = 3
    val end = if (artifactNames.size > maxArtifacts) "..." else ""
    artifactNames.toList.take(maxArtifacts).mkString("", ", ", end)
  }

  private def single(groupId: String, artifactId: String, includeGroupId: Boolean): String = {
    val groupName = groupId.split('.').lastOption.getOrElse(groupId)
    if (!includeGroupId || artifactId.contains(groupName)) artifactId
    else groupName + ":" + artifactId
  }
}
