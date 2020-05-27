/*
 * Copyright 2018-2020 Scala Steward contributors
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

import cats.Functor
import cats.implicits._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Version}
import org.scalasteward.core.io.{FileAlg, FileData}

package object sbt {
  val defaultScalaBinaryVersion: String =
    org.scalasteward.core.BuildInfo.scalaBinaryVersion

  def sbtDependency(sbtVersion: SbtVersion): Option[Dependency] =
    if (sbtVersion.toVersion >= Version("1.0.0"))
      Some(
        Dependency(
          GroupId("org.scala-sbt"),
          ArtifactId("sbt"),
          sbtVersion.value
        )
      )
    else
      None

  val scalaStewardScalafixSbt: FileData =
    FileData(
      "scala-steward-scalafix.sbt",
      """addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.16")"""
    )

  def stewardPlugin[F[_]](implicit fileAlg: FileAlg[F], F: Functor[F]): F[FileData] = {
    val name = "StewardPlugin.scala"
    fileAlg.readResource(s"org/scalasteward/plugin/$name").map(FileData(name, _))
  }
}
