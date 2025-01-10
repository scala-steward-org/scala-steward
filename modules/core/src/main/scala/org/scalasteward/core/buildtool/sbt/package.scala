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

package org.scalasteward.core.buildtool

import cats.syntax.all.*
import org.scalasteward.core.buildtool.sbt.data.{SbtVersion, ScalaVersion}
import org.scalasteward.core.data.*
import org.scalasteward.core.io.FileData

package object sbt {
  val defaultScalaBinaryVersion: String =
    org.scalasteward.core.BuildInfo.scalaBinaryVersion

  val sbtGroupId: GroupId = GroupId("org.scala-sbt")

  val sbtArtifactId: ArtifactId = ArtifactId("sbt")

  val buildPropertiesName = "build.properties"

  def isSbtUpdate(update: Update.Single): Boolean =
    update.groupId === sbtGroupId &&
      update.artifactIds.exists(_.name === sbtArtifactId.name)

  def sbtDependency(version: Version): Option[Dependency] =
    Option.when(version >= Version("1.0.0"))(Dependency(sbtGroupId, sbtArtifactId, version))

  val sbtScalafixGroupId: GroupId = GroupId("ch.epfl.scala")

  val sbtScalafixArtifactId: ArtifactId = ArtifactId("sbt-scalafix")

  val sbtScalafixDependency: Dependency =
    Dependency(
      sbtScalafixGroupId,
      sbtScalafixArtifactId,
      Version(""),
      Some(SbtVersion("1.0")),
      Some(ScalaVersion("2.12"))
    )

  def scalaStewardSbtScalafix(version: Version): FileData = {
    val content =
      s"""addSbtPlugin("${sbtScalafixGroupId.value}" % "${sbtScalafixArtifactId.name}" % "$version")"""
    FileData("scala-steward-sbt-scalafix.sbt", content)
  }

  def scalaStewardScalafixOptions(scalacOptions: List[String]): FileData = {
    val args = scalacOptions.map(s => s""""$s"""").mkString(", ")
    FileData("scala-steward-scalafix-options.sbt", s"ThisBuild / scalacOptions ++= List($args)")
  }
}
