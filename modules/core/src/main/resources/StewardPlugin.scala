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

import sbt.Keys._
import sbt._

object StewardPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val libraryDependenciesAsJson = taskKey[String]("")
  }

  import autoImport._

  def crossName(moduleId: ModuleID, scalaVersion: String, scalaBinaryVersion: String): String =
    CrossVersion(moduleId.crossVersion, scalaVersion, scalaBinaryVersion)
      .getOrElse(identity[String](_))(moduleId.name)

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependenciesAsJson := {
      val sourcePositions = dependencyPositions.value
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value

      val deps = libraryDependencies.value.filter(isDefinedInBuildFiles(_, sourcePositions)).map {
        moduleId =>
          val entries: List[(String, String)] = List(
            "groupId" -> moduleId.organization,
            "artifactId" -> moduleId.name,
            "artifactIdCross" -> crossName(moduleId, scalaVersionValue, scalaBinaryVersionValue),
            "version" -> moduleId.revision
          ) ++
            moduleId.extraAttributes.get("e:sbtVersion").map("sbtVersion" -> _).toList ++
            moduleId.configurations.map("configurations" -> _).toList

          entries.map { case (k, v) => s""""$k": "$v"""" }.mkString("{ ", ", ", " }")
      }
      deps.mkString("[ ", ", ", " ]")
    }
  )

  // Inspired by https://github.com/rtimush/sbt-updates/issues/42
  private def isDefinedInBuildFiles(
      moduleId: ModuleID,
      sourcePositions: Map[ModuleID, SourcePosition]
  ): Boolean =
    sourcePositions.get(moduleId) match {
      case Some(fp: FilePosition) if fp.path.startsWith("(sbt.Classpaths") => true
      case Some(fp: FilePosition) if fp.path.startsWith("(")               => false
      case Some(fp: FilePosition)
          if fp.path.startsWith("Defaults.scala")
            && !moduleId.configurations.exists(_ == "plugin->default(compile)") =>
        false
      case _ => true
    }
}
