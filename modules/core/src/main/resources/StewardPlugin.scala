/*
 * Copyright 2018 scala-steward contributors
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

package sbt.scalasteward

import sbt.Keys._
import sbt._

object StewardPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val libraryDependenciesAsJson = settingKey[String]("")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependenciesAsJson := {
      val deps = libraryDependencies.value.map {
        moduleId =>
          val cross =
            CrossVersion(moduleId.crossVersion, scalaVersion.value, scalaBinaryVersion.value)
          val crossName = CrossVersion.applyCross(moduleId.name, cross)

          val entries: List[(String, String)] = List(
            "groupId" -> moduleId.organization,
            "artifactId" -> moduleId.name,
            //"crossArtifactId" -> crossName,
            "version" -> moduleId.revision,
            "scalaVersion" -> moduleId.extraAttributes
              .getOrElse("e:scalaVersion", scalaVersion.value)
          ) ++
            moduleId.extraAttributes
              .get("e:sbtVersion")
              .map(sbtVersion => "sbtVersion" -> sbtVersion)
              .toList

          entries.map { case (k, v) => s""""$k": "$v"""" }.mkString("{ ", ", ", " }")
      }
      deps.mkString("[ ", ", ", " ]")
    }
  )
}
