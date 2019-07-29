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

import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalasteward.core.data.Update
import org.scalasteward.core.io.FileData
import org.scalasteward.core.sbt.data.{SbtVersion, ScalaVersion}
import org.scalasteward.core.util.Nel
import scala.io.Source

package object sbt {
  val defaultSbtVersion: SbtVersion =
    SbtVersion(BuildInfo.sbtVersion)

  // Needs manual update
  val latestSbtVersion_0_13: SbtVersion =
    SbtVersion("0.13.18")

  val defaultScalaVersion: ScalaVersion =
    ScalaVersion(BuildInfo.scalaVersion)

  def findNewerSbtVersion(sbtVersion: SbtVersion): Option[SbtVersion] =
    (sbtVersion.value match {
      case v if v.startsWith("0.13.")    => Some(latestSbtVersion_0_13)
      case v if v.startsWith("1.3.0-RC") => Some(SbtVersion("1.3.0-RC3"))
      case v if v.startsWith("1.")       => Some(defaultSbtVersion)
      case _                             => None
    }).filter(_.toVersion > sbtVersion.toVersion)

  def findSbtUpdate(currentVersion: SbtVersion): Option[Update.Single] =
    findNewerSbtVersion(currentVersion).map { newerVersion =>
      Update.Single("org.scala-sbt", "sbt", currentVersion.value, Nel.of(newerVersion.value))
    }

  def seriesToSpecificVersion(sbtSeries: SbtVersion): SbtVersion =
    sbtSeries.value match {
      case "0.13" => latestSbtVersion_0_13
      case "1.0"  => defaultSbtVersion
      case _      => defaultSbtVersion
    }

  val scalaStewardSbt: FileData =
    FileData(
      "scala-steward.sbt",
      """addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.4.2")"""
    )

  val scalaStewardScalafixSbt: FileData =
    FileData(
      "scala-steward-scalafix.sbt",
      """addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.5")"""
    )

  val stewardPlugin: FileData = {
    val name = "StewardPlugin.scala"
    // I don't consider reading a resource as side-effect,
    // so it is OK to call `unsafeRunSync` here.
    Resource
      .fromAutoCloseable(IO(Source.fromResource(name)))
      .use(src => IO(FileData(name, src.mkString)))
      .unsafeRunSync()
  }
}
