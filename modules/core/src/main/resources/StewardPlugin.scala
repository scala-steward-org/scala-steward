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

import com.timushev.sbt.updates.UpdatesKeys.dependencyUpdatesData
import com.timushev.sbt.updates.versions.ValidVersion
import sbt._
import sbt.Keys._

object StewardPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val stewardDependencies = taskKey[String]("")
    val stewardUpdates = taskKey[String]("")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    stewardDependencies := {
      val sourcePositions = dependencyPositions.value
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value

      val dependencies = libraryDependencies.value
        .filter(isDefinedInBuildFiles(_, sourcePositions))
        .map(moduleId => toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue))

      multilineJson(dependencies)
    },
    stewardUpdates := {
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value

      val updates = dependencyUpdatesData.value.toList.map {
        case (moduleId, newerVersions) =>
          val validNewerVersions = newerVersions.toList.collect { case v: ValidVersion => v.text }
          toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue)
            .copy(newerVersions = Some(validNewerVersions))
      }
      val updatesWithoutNewerVersions = updates.map(_.copy(newerVersions = None))
      val dependencies = libraryDependencies.value
        .map(toDependency(_, scalaVersionValue, scalaBinaryVersionValue))
        .filterNot(updatesWithoutNewerVersions.contains)

      multilineJson(updates ++ dependencies)
    }
  )

  private def crossName(
      moduleId: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String
  ): Option[String] =
    CrossVersion(moduleId.crossVersion, scalaVersion, scalaBinaryVersion).map(_(moduleId.name))

  private def toDependency(
      moduleId: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String
  ): Dependency =
    Dependency(
      groupId = moduleId.organization,
      artifactId = moduleId.name,
      crossArtifactIds = crossName(moduleId, scalaVersion, scalaBinaryVersion).toList,
      version = moduleId.revision,
      newerVersions = None,
      configurations = moduleId.configurations,
      sbtSeries = moduleId.extraAttributes.get("e:sbtVersion")
    )

  private def multilineJson(dependencies: Seq[Dependency]): String =
    dependencies
      .sortBy(dep => (dep.groupId, dep.artifactId))
      .map(_.asJson)
      .mkString(System.lineSeparator())

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

  final private case class Dependency(
      groupId: String,
      artifactId: String,
      crossArtifactIds: List[String],
      version: String,
      newerVersions: Option[List[String]],
      configurations: Option[String],
      sbtSeries: Option[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "groupId" -> strToJson(groupId),
          "artifactId" -> strToJson(artifactId),
          "crossArtifactIds" -> seqToJson(crossArtifactIds.map(strToJson)),
          "version" -> strToJson(version),
          "newerVersions" -> newerVersions.fold("null")(vs => seqToJson(vs.map(strToJson))),
          "configurations" -> optToJson(configurations.map(strToJson)),
          "sbtSeries" -> optToJson(sbtSeries.map(strToJson))
        )
      )
  }

  private def strToJson(str: String): String =
    s""""$str""""

  private def optToJson(opt: Option[String]): String =
    opt.getOrElse("null")

  private def seqToJson(seq: Seq[String]): String =
    seq.mkString("[ ", ", ", " ]")

  private def objToJson(obj: List[(String, String)]): String =
    obj.map { case (k, v) => s""""$k": $v""" }.mkString("{ ", ", ", " }")
}
