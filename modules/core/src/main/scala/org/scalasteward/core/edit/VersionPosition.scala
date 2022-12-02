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

package org.scalasteward.core.edit

import org.scalasteward.core.io.FilePosition

sealed trait ModulePosition

sealed trait VersionPosition extends Product with Serializable {
  def filePosition: FilePosition
}

object VersionPosition {
  final case class SbtModuleId(filePosition: FilePosition) extends VersionPosition {
    def isCommented: Boolean = ???
  }

  final case class ScalaVal(
      filePosition: FilePosition,
      name: String,
      before: String
  ) extends VersionPosition {
    def isCommented: Boolean = before.contains("//")
  }

  final case class Unclassified(filePosition: FilePosition) extends VersionPosition
}
