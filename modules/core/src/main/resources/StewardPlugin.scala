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

import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers._

object StewardPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val stewardDependencies = inputKey[String]("")
    val stewardDependenciesWithUpdates = inputKey[String]("")
  }

  import autoImport._

  def crossName(moduleId: ModuleID, scalaVersion: String, scalaBinaryVersion: String): String =
    CrossVersion(moduleId.crossVersion, scalaVersion, scalaBinaryVersion)
      .getOrElse(identity[String](_))(moduleId.name)

  def toDependency(
      moduleId: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String,
      label: Option[String]
  ): Dependency =
    Dependency(
      groupId = moduleId.organization,
      artifactId = moduleId.name,
      artifactIdCross = crossName(moduleId, scalaVersion, scalaBinaryVersion),
      version = moduleId.revision,
      newerVersions = List.empty,
      configurations = moduleId.configurations,
      label = label
    )

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    stewardDependencies := {
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value
      val label = spaceDelimited("<label>").parsed.headOption

      val deps = libraryDependencies.value.map { moduleId =>
        toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue, label).asJson
      }
      seqToJson(deps)
    },
    stewardDependenciesWithUpdates := {
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value
      val label = spaceDelimited("<label>").parsed.headOption

      val updatesData = com.timushev.sbt.updates.UpdatesKeys.dependencyUpdatesData.value
      val updates = updatesData.toList.map {
        case (moduleId, newerVersions) =>
          toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue, label)
            .copy(newerVersions = newerVersions.toList.map(_.toString))
      }
      val dependencies = libraryDependencies.value
        .map(toDependency(_, scalaVersionValue, scalaBinaryVersionValue, label))
        .filterNot(d => updates.exists(u => d == u.copy(newerVersions = List.empty)))
      seqToJson((updates ++ dependencies).map(_.asJson))
    }
  )

  final case class Dependency(
      groupId: String,
      artifactId: String,
      artifactIdCross: String,
      version: String,
      newerVersions: List[String],
      configurations: Option[String],
      label: Option[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "groupId" -> strToJson(groupId),
          "artifactId" -> strToJson(artifactId),
          "artifactIdCross" -> strToJson(artifactIdCross),
          "version" -> strToJson(version),
          "newerVersions" -> seqToJson(newerVersions.map(strToJson)),
          "configurations" -> optToJson(configurations.map(strToJson)),
          "label" -> optToJson(label.map(strToJson))
        )
      )
  }

  def strToJson(str: String): String =
    s""""$str""""

  def optToJson(opt: Option[String]): String =
    opt.getOrElse("null")

  def seqToJson(seq: Seq[String]): String =
    seq.mkString("[ ", ", ", " ]")

  def objToJson(obj: List[(String, String)]): String =
    obj.map { case (k, v) => s""""$k": $v""" }.mkString("{ ", ", ", " }")
}
