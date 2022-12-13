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

package org.scalasteward.core.edit.update.data

sealed trait VersionPosition extends Product with Serializable {
  def version: Substring.Position
  def isCommented: Boolean
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
  ) extends DependencyDef {
    override def isCommented: Boolean = false
  }

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
  ) extends VersionPosition {
    override def isCommented: Boolean = false
  }
}