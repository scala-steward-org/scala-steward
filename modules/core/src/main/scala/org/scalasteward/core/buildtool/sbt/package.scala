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

package org.scalasteward.core.buildtool

import cats.Functor
import cats.syntax.all._
import org.scalasteward.core.buildtool.sbt.data.{SbtVersion, ScalaVersion}
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Version}
import org.scalasteward.core.io.{FileAlg, FileData}

package object sbt {
  val defaultScalaBinaryVersion: String =
    org.scalasteward.core.BuildInfo.scalaBinaryVersion

  val sbtGroupId: GroupId = GroupId("org.scala-sbt")

  val sbtArtifactId: ArtifactId = ArtifactId("sbt")

  def sbtDependency(sbtVersion: SbtVersion): Option[Dependency] = {
    val version = sbtVersion.toVersion
    Option.when(version >= Version("1.0.0"))(Dependency(sbtGroupId, sbtArtifactId, version))
  }

  val sbtScalaFixGroupId = GroupId("ch.epfl.scala")
  val sbtScalaFixArtifactId = ArtifactId("sbt-scalafix")

  val sbtScalaFixDependency: Dependency =
    Dependency(
      sbtScalaFixGroupId,
      sbtScalaFixArtifactId,
      Version(""),
      Some(SbtVersion("1.0")),
      Some(ScalaVersion("2.12"))
    )

  def scalaStewardScalafixSbt(version: String): FileData =
    FileData(
      "scala-steward-scalafix.sbt",
      s"""addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "$version")"""
    )

  def scalaStewardScalafixOptions(scalacOptions: List[String]): FileData = {
    val args = scalacOptions.map(s => s""""$s"""").mkString(", ")
    FileData("scala-steward-scalafix-options.sbt", s"ThisBuild / scalacOptions ++= List($args)")
  }

  def stewardPlugin[F[_]](implicit fileAlg: FileAlg[F], F: Functor[F]): F[FileData] = {
    val pkg: String = org.scalasteward.core.BuildInfo.sbtPluginModuleRootPkg
    val name = "StewardPlugin.scala"
    fileAlg
      .readResource(s"${pkg.replace('.', '/')}/$name")
      .map(FileData(name, _))
  }
}
