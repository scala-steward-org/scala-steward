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

/** A position of a version in a file.
  *
  * This trait is used to classify different version positions (e.g. if they are part of a
  * dependency definition or the right hand side of a `val`) and to retain some surrounding context.
  *
  * The classification and context is used in [[org.scalasteward.core.edit.update.Selector]] to
  * decide if it should be updated or not.
  */
sealed trait VersionPosition extends Product with Serializable {
  def version: Substring.Position
}

object VersionPosition {
  sealed trait DependencyDef extends VersionPosition {
    def groupId: String
    def artifactId: String
  }

  final case class SbtDependency(
      version: Substring.Position,
      before: String,
      groupId: String,
      artifactId: String
  ) extends DependencyDef {
    def isCommented: Boolean = before.contains("//")
  }

  final case class MillDependency(
      version: Substring.Position,
      before: String,
      groupId: String,
      artifactId: String
  ) extends DependencyDef {
    def isCommented: Boolean = before.contains("//")
  }

  final case class MavenDependency(
      version: Substring.Position,
      groupId: String,
      artifactId: String
  ) extends DependencyDef

  final case class ScalaVal(
      version: Substring.Position,
      before: String,
      name: String
  ) extends VersionPosition {
    def isCommented: Boolean = before.contains("//")
  }

  final case class Unclassified(
      version: Substring.Position,
      before: String
  ) extends VersionPosition
}
