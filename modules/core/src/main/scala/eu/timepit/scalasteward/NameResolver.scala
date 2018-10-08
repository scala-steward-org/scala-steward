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

package eu.timepit.scalasteward

import cats.data.NonEmptyList
import eu.timepit.scalasteward.model.Update

object NameResolver {

  def resolve(update: Update): String =
    update match {
      case u: Update.Single => single(u.groupId, u.artifactId)
      case u: Update.Group  => group(u.groupId, u.artifactIds)
    }

  private def group(groupId: String, artifactIds: NonEmptyList[String]): String =
    if (artifactIds.size > 3)
      artifactIds.toList.take(3).map(single(groupId, _)).mkString(", ") + "..."
    else artifactIds.toList.map(single(groupId, _)).mkString(", ")

  private def single(groupId: String, artifactId: String): String = {
    val groupName = groupId.split("\\.").lastOption.getOrElse(groupId)
    if (artifactId.contains(groupName)) artifactId
    else groupName + ":" + artifactId
  }
}
