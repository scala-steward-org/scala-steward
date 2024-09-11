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
  // https://github.com/JetBrains/bazel-bsp#easy-way-coursier
  // Status: waiting for https://youtrack.jetbrains.com/issue/BAZEL-616
  case object Bazel
      extends BspServerType(
        connectionDetailsCommand = Nel.of(
          "cs",
          "launch",
          org.scalasteward.core.BuildInfo.bazelBsp,
          "-M",
          "org.jetbrains.bsp.bazel.install.Install"
        ),
        connectionDetailsName = "bazelbsp.json"
      )

  // https://bleep.build/
  // Status: waiting for https://github.com/scalacenter/bloop/pull/2197 to propagate to bleep
  case object Bleep
      extends BspServerType(
        connectionDetailsCommand = Nel.of("bleep", "setup-ide"),
        connectionDetailsName = "bleep.json"
      )

  // https://github.com/microsoft/build-server-for-gradle
  // Status: waiting for https://github.com/microsoft/build-server-for-gradle/issues/115
  case object Gradle
      extends BspServerType(
        connectionDetailsCommand = Nel.one("???"),
        connectionDetailsName = "???"
      )

  // https://com-lihaoyi.github.io/mill/mill/Plugin_BSP.html
  // Status: working
  case object Mill
      extends BspServerType(
        connectionDetailsCommand = Nel.of("mill", "mill.bsp.BSP/install"),
        connectionDetailsName = "mill-bsp.json"
      )

  // https://www.scala-sbt.org
  // Status: waiting for https://github.com/sbt/sbt/issues/6957
  case object Sbt
      extends BspServerType(
        connectionDetailsCommand = Nel.of("sbt", "bspConfig"),
        connectionDetailsName = "sbt.json"
      )

  // https://scala-cli.virtuslab.org/docs/commands/setup-ide#ide-support-internals
  // Status: working
  case object ScalaCli
      extends BspServerType(
        connectionDetailsCommand = Nel.of("scala-cli", "setup-ide", "."),
        connectionDetailsName = "scala-cli.json"
      )
}
