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

package org.scalasteward.core.edit.update.data

/** The positions of a groupId, artifactId, and version.
  *
  * The version is not necessarily the literal version string but could also be a reference to a
  * variable. This class is used in [[org.scalasteward.core.edit.update.Selector]] to find the
  * corresponding version position if a `val` is used for the version and to change the groupId
  * and/or artifactId for artifact migrations.
  */
final case class ModulePosition(
    groupId: Substring.Position,
    artifactId: Substring.Position,
    version: Substring.Position
) {
  def unwrappedVersion: String =
    version.value.dropWhile(Set('"', '$', '{')).reverse.dropWhile(Set('"', '}', ',')).reverse
}
