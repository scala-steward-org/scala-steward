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

package org.scalasteward.core.edit.update

import org.scalasteward.core.data.Dependency
import org.scalasteward.core.edit.update.data.{ModulePosition, Substring}
import org.scalasteward.core.io.FileData
import scala.util.matching.Regex

/** Finds all module positions for a given dependency in a file. */
object ModulePositionScanner {
  def findPositions(dependency: Dependency, fileData: FileData): List[ModulePosition] = {
    val offRegions = findOffRegions(fileData.content)
    val it = findSbtDependency(dependency, fileData) ++
      findMillDependency(dependency, fileData) ++
      findMavenDependency(dependency, fileData)
    it.filterNot(p => isInside(p.version.start, offRegions)).distinctBy(_.groupId.start).toList
  }

  private def findSbtDependency(
      dependency: Dependency,
      fileData: FileData
  ): Iterator[ModulePosition] =
    sbtModuleIdRegex(dependency).findAllIn(fileData.content).matchData.map { m =>
      val groupId = Substring.Position(fileData.path, m.start(1), dependency.groupId.value)
      val artifactId = Substring.Position(fileData.path, m.start(2), dependency.artifactId.name)
      val version = Substring.Position(fileData.path, m.start(3), m.group(3))
      ModulePosition(groupId, artifactId, version)
    }

  private def sbtModuleIdRegex(dependency: Dependency): Regex = {
    val g = Regex.quote(dependency.groupId.value)
    val a = Regex.quote(dependency.artifactId.name)
    raw""""($g)"\s*%+\s*"($a)"\s*%+\s*([^\s/]*)""".r
  }

  private def findMillDependency(
      dependency: Dependency,
      fileData: FileData
  ): Iterator[ModulePosition] =
    millDependencyRegex(dependency).findAllIn(fileData.content).matchData.map { m =>
      val groupId = Substring.Position(fileData.path, m.start(1), dependency.groupId.value)
      val artifactId = Substring.Position(fileData.path, m.start(2), dependency.artifactId.name)
      val version = Substring.Position(fileData.path, m.start(3), m.group(3))
      ModulePosition(groupId, artifactId, version)
    }

  private def millDependencyRegex(dependency: Dependency): Regex = {
    val g = Regex.quote(dependency.groupId.value)
    val a = Regex.quote(dependency.artifactId.name)
    raw"""["`]($g):+($a):+(.*)["`;]""".r
  }

  private def findMavenDependency(
      dependency: Dependency,
      fileData: FileData
  ): Iterator[ModulePosition] =
    mavenDependencyRegex(dependency).findAllIn(fileData.content).matchData.map { m =>
      val groupId = Substring.Position(fileData.path, m.start(1), dependency.groupId.value)
      val artifactId = Substring.Position(fileData.path, m.start(2), dependency.artifactId.name)
      val version = Substring.Position(fileData.path, m.start(4), m.group(4))
      ModulePosition(groupId, artifactId, version)
    }

  private def mavenDependencyRegex(dependency: Dependency): Regex = {
    val g = Regex.quote(dependency.groupId.value)
    val a = Regex.quote(dependency.artifactId.name)
    raw"""<groupId>($g)</groupId>\s*<artifactId>($a)(|_[^<]+)</artifactId>\s*<version>(.*)</version>""".r
  }
}
