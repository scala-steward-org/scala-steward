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

package org.scalasteward.core

import cats.syntax.all.*
import org.scalasteward.core.buildtool.sbt.defaultScalaBinaryVersion
import org.scalasteward.core.data.*

package object scalafmt {
  val scalafmtGroupId: GroupId =
    GroupId("org.scalameta")

  val scalafmtArtifactId: ArtifactId = {
    val core = "scalafmt-core"
    ArtifactId(core, s"${core}_$defaultScalaBinaryVersion")
  }

  val scalafmtModule: (GroupId, ArtifactId) =
    (scalafmtGroupId, scalafmtArtifactId)

  def isScalafmtCoreUpdate(update: Update.Single): Boolean =
    update.groupId === scalafmtGroupId &&
      update.artifactIds.exists(_.name === scalafmtArtifactId.name)

  private def scalafmtGroupIdBy(version: Version): GroupId =
    if (version > Version("2.0.0-RC1")) scalafmtGroupId else GroupId("com.geirsson")

  def scalafmtDependency(version: Version): Dependency =
    Dependency(scalafmtGroupIdBy(version), scalafmtArtifactId, version)

  val scalafmtBinary: String = "scalafmt"

  val scalafmtConfName: String = ".scalafmt.conf"
}
