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

import org.scalasteward.core.data.Version
import org.scalasteward.core.edit.update.data.VersionPosition.*
import org.scalasteward.core.edit.update.data.{Substring, VersionPosition}
import org.scalasteward.core.io.FileData
import scala.util.matching.Regex

/** Finds all version positions for a given version in a file. */
object VersionPositionScanner {
  def findPositions(version: Version, fileData: FileData): List[VersionPosition] = {
    val offRegions = findOffRegions(fileData.content)
    val it = findSbtDependency(version, fileData) ++
      findMillDependency(version, fileData) ++
      findMavenDependency(version, fileData) ++
      findScalaVal(version, fileData) ++
      findUnclassified(version, fileData)
    it.filterNot(p => isInside(p.version.start, offRegions)).distinctBy(_.version.start).toList
  }

  private def findSbtDependency(version: Version, fileData: FileData): Iterator[SbtDependency] =
    sbtModuleIdRegex(version).findAllIn(fileData.content).matchData.map { m =>
      val versionPos = Substring.Position.fromMatch(fileData.path, m, version.value)
      val before = m.group(1)
      val groupId = m.group(2)
      val artifactId = m.group(3)
      SbtDependency(versionPos, before, groupId, artifactId)
    }

  private def sbtModuleIdRegex(version: Version): Regex = {
    val ident = """[^\s]+"""
    val v = Regex.quote(version.value)
    raw"""(.*)"($ident)"\s*%+\s*"($ident)"\s*%+\s*"$v"""".r
  }

  private def findMillDependency(version: Version, fileData: FileData): Iterator[MillDependency] =
    millDependencyRegex(version).findAllIn(fileData.content).matchData.map { m =>
      val versionPos = Substring.Position.fromMatch(fileData.path, m, version.value)
      val before = m.group(1)
      val groupId = m.group(2)
      val artifactId = m.group(3)
      MillDependency(versionPos, before, groupId, artifactId)
    }

  private def millDependencyRegex(version: Version): Regex = {
    val ident = """[^:\s]+"""
    val v = Regex.quote(version.value)
    raw"""(.*)["`]($ident):+($ident):+$v["`;]""".r
  }

  private def findMavenDependency(version: Version, fileData: FileData): Iterator[MavenDependency] =
    mavenDependencyRegex(version).findAllIn(fileData.content).matchData.map { m =>
      val versionPos = Substring.Position.fromMatch(fileData.path, m, version.value)
      val groupId = m.group(1)
      val artifactId = m.group(2)
      MavenDependency(versionPos, groupId, artifactId)
    }

  private def mavenDependencyRegex(version: Version): Regex = {
    val ident = """[^<]+"""
    val v = Regex.quote(version.value)
    raw"""<groupId>($ident)</groupId>\s*<artifactId>($ident)</artifactId>\s*<version>$v</version>""".r
  }

  private def findScalaVal(version: Version, fileData: FileData): Iterator[ScalaVal] =
    scalaValRegex(version).findAllIn(fileData.content).matchData.map { m =>
      val versionPos = Substring.Position.fromMatch(fileData.path, m, version.value)
      val before = m.group(1)
      val name = m.group(3)
      ScalaVal(versionPos, before, name)
    }

  private def scalaValRegex(version: Version): Regex = {
    val ident = """[^=]+?"""
    val v = Regex.quote(version.value)
    raw"""(.*)(def|val)\s+($ident)\s*=\s*"$v"""".r
  }

  private def findUnclassified(version: Version, fileData: FileData): Iterator[Unclassified] = {
    val v = Regex.quote(version.value)
    val regex = raw"""(.*)$v(.?)""".r
    regex.findAllIn(fileData.content).matchData.flatMap { m =>
      val versionPos = Substring.Position.fromMatch(fileData.path, m, version.value)
      val before = m.group(1)
      val after = Option(m.group(2))
      val leadingChar = before.lastOption
      val trailingChar = after.flatMap(_.headOption)
      Option.when(
        !leadingChar.exists(_.isLetterOrDigit) &&
          !trailingChar.exists(_.isLetterOrDigit) &&
          !trailingChar.exists(Set('.', '-', '+'))
      ) {
        Unclassified(versionPos, before)
      }
    }
  }
}
