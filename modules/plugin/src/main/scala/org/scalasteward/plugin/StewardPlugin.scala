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

package org.scalasteward.plugin

import com.timushev.sbt.updates.UpdatesKeys.dependencyUpdatesData
import com.timushev.sbt.updates.versions.{InvalidVersion, ValidVersion}
import sbt.Keys._
import sbt._
import scala.util.matching.Regex

object StewardPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val stewardDependencies =
      taskKey[Unit]("Prints dependencies as JSON for consumption by Scala Steward.")
    val stewardUpdates =
      taskKey[Unit]("Prints dependency updates as JSON for consumption by Scala Steward.")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    stewardDependencies := {
      val log = streams.value.log
      val sourcePositions = dependencyPositions.value
      val buildRoot = baseDirectory.in(ThisBuild).value
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value

      val dependencies = libraryDependencies.value
        .filter(isDefinedInBuildFiles(_, sourcePositions, buildRoot))
        .map(moduleId => toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue))

      dependencies.map(_.asJson).foreach(s => log.info(s))
    },
    stewardUpdates := {
      val log = streams.value.log
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value

      val updates = dependencyUpdatesData.value.toList.map {
        case (moduleId, versions) =>
          Update(
            dependency = toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue),
            newerVersions = versions.toList.map {
              case v: ValidVersion   => v.text
              case v: InvalidVersion => v.text
            }
          )
      }

      updates.map(_.asJson).foreach(s => log.info(s))
    }
  )

  // Inspired by https://github.com/rtimush/sbt-updates/issues/42 and
  // https://github.com/rtimush/sbt-updates/pull/112
  private def isDefinedInBuildFiles(
      moduleId: ModuleID,
      sourcePositions: Map[ModuleID, SourcePosition],
      buildRoot: File
  ): Boolean =
    sourcePositions.get(moduleId) match {
      case Some(fp: FilePosition) =>
        val path = fp.path
        () match {
          case _ if path.startsWith("(sbt.Classpaths") => true
          case _ if path.startsWith("(") =>
            extractFileName(path).exists(fileExists(buildRoot, _))

          // Compiler plugins added via addCompilerPlugin(...) have a SourcePosition
          // like this: LinePosition(Defaults.scala,3738).
          case _ if path.startsWith("Defaults.scala") && isCompilerPlugin(moduleId) => true
          case _ if path.startsWith("Defaults.scala")                               => false
          case _                                                                    => true
        }
      case _ => true
    }

  private def extractFileName(path: String): Option[String] = {
    val FileNamePattern: Regex = "^\\([^\\)]+\\) (.*)$".r
    path match {
      case FileNamePattern(fileName) => Some(fileName)
      case _                         => None
    }
  }

  private def fileExists(buildRoot: File, file: String): Boolean =
    (buildRoot / "project" / file).exists()

  private def isCompilerPlugin(moduleId: ModuleID): Boolean =
    moduleId.configurations.exists(_ == "plugin->default(compile)")

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
      artifactId = ArtifactId(moduleId.name, crossName(moduleId, scalaVersion, scalaBinaryVersion)),
      version = moduleId.revision,
      sbtVersion = moduleId.extraAttributes.get("e:sbtVersion"),
      scalaVersion = moduleId.extraAttributes.get("e:scalaVersion"),
      configurations = moduleId.configurations
    )

  final private case class ArtifactId(
      name: String,
      maybeCrossName: Option[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "name" -> strToJson(name),
          "maybeCrossName" -> optToJson(maybeCrossName.map(strToJson))
        )
      )
  }

  final private case class Dependency(
      groupId: String,
      artifactId: ArtifactId,
      version: String,
      sbtVersion: Option[String],
      scalaVersion: Option[String],
      configurations: Option[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "groupId" -> strToJson(groupId),
          "artifactId" -> artifactId.asJson,
          "version" -> strToJson(version),
          "sbtVersion" -> optToJson(sbtVersion.map(strToJson)),
          "scalaVersion" -> optToJson(scalaVersion.map(strToJson)),
          "configurations" -> optToJson(configurations.map(strToJson))
        )
      )
  }

  final private case class Update(
      dependency: Dependency,
      newerVersions: List[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "dependency" -> dependency.asJson,
          "newerVersions" -> seqToJson(newerVersions.map(strToJson))
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
