/*
 * Copyright 2018-2021 Scala Steward contributors
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

import cats.syntax.all._
import org.scalasteward.core.buildtool.sbt.defaultScalaBinaryVersion
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Version}

package object scalafmt {
  val scalafmtGroupId: GroupId =
    GroupId("org.scalameta")

  val scalafmtArtifactId: ArtifactId =
    ArtifactId("scalafmt-core", s"scalafmt-core_$defaultScalaBinaryVersion")

  val scalafmtBinary: String = "scalafmt"

  def scalafmtDependency(scalafmtVersion: Version): Dependency =
    Dependency(
      if (scalafmtVersion > Version("2.0.0-RC1")) scalafmtGroupId else GroupId("com.geirsson"),
      scalafmtArtifactId,
      scalafmtVersion.value
    )

  def parseScalafmtConf(s: String): Option[Version] =
    """version\s*=\s*(.+)""".r
      .findFirstMatchIn(s)
      .map(_.group(1).replace("\"", ""))
      .map(Version.apply)
}
