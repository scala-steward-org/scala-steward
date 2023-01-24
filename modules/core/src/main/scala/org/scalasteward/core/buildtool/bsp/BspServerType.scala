/*
 * Copyright 2018-2023 Scala Steward contributors
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

package org.scalasteward.core.buildtool.bsp

import org.scalasteward.core.util.Nel

sealed abstract class BspServerType(
    val connectionDetailsCommand: Nel[String],
    val connectionDetailsName: String
)

object BspServerType {
  case object Mill
      extends BspServerType(
        connectionDetailsCommand = Nel.of("mill", "mill.bsp.BSP/install"),
        connectionDetailsName = "mill-bsp.json"
      )

  case object Sbt
      extends BspServerType(
        connectionDetailsCommand = Nel.of("sbt", "bspConfig"),
        connectionDetailsName = "sbt.json"
      )
}
