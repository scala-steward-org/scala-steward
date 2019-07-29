/*
 * Copyright 2018-2019 Scala Steward contributors
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

import cats.implicits._
import org.scalasteward.core.data.{Update, Version}
import org.scalasteward.core.util.Nel

package object scalafmt {
  val latestScalafmtVersion: Version = Version("2.0.0")
  val scalafmtGroupId = "org.scalameta"
  val scalafmtArtifactId = "scalafmt"

  def findNewerScalafmtVersion(currentVersion: Version): Option[Version] =
    if (Version.versionOrder.lt(currentVersion, latestScalafmtVersion))
      Some(latestScalafmtVersion)
    else
      None

  def parseScalafmtConf(s: String): Option[Version] =
    """version\s*=\s*(.+)""".r
      .findFirstMatchIn(s)
      .map(_.group(1).replaceAllLiterally("\"", ""))
      .map(Version.apply)

  def findScalafmtUpdate(currentVersion: Version): Option[Update.Single] =
    findNewerScalafmtVersion(currentVersion).map { newerVersion =>
      Update.Single(
        scalafmtGroupId,
        scalafmtArtifactId,
        currentVersion.value,
        Nel.of(newerVersion.value)
      )
    }

  def isScalafmtUpdate(update: Update): Boolean =
    update.groupId === scalafmtGroupId && update.artifactId === scalafmtArtifactId
}
