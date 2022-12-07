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

package org.scalasteward.core.edit.update

import org.scalasteward.core.data.Dependency
import org.scalasteward.core.edit.update.data.ModulePosition._
import org.scalasteward.core.edit.update.data.{ModulePosition, SubstringPosition}
import scala.util.matching.Regex

object ModuleScanner {
  def findPositions(dependency: Dependency, content: String): List[ModulePosition] = {
    val it = findSbtModuleId(dependency, content) ++
      findMillDependency(dependency, content) ++
      findMavenDependency(dependency, content)
    it.distinctBy(_.groupId.start).toList
  }

  private def findSbtModuleId(dependency: Dependency, content: String): Iterator[SbtModuleId] =
    sbtModuleIdRegex(dependency).findAllIn(content).matchData.map { m =>
      val groupId = SubstringPosition.fromMatch(m, dependency.groupId.value)
      val artifactId = SubstringPosition.fromMatch(m, dependency.artifactId.name)
      val version = SubstringPosition.fromMatch(m, m.group(1))
      SbtModuleId(groupId, artifactId, version)
    }

  private def sbtModuleIdRegex(dependency: Dependency): Regex = {
    val g = Regex.quote(dependency.groupId.value)
    val a = Regex.quote(dependency.artifactId.name)
    raw""""$g"\s*%+\s*"$a"\s*%+\s*(.*)""".r
  }

  private def findMillDependency(
      dependency: Dependency,
      content: String
  ): Iterator[MillDependency] =
    millDependencyRegex(dependency).findAllIn(content).matchData.map { m =>
      val groupId = SubstringPosition.fromMatch(m, dependency.groupId.value)
      val artifactId = SubstringPosition.fromMatch(m, dependency.artifactId.name)
      val version = SubstringPosition.fromMatch(m, m.group(1))
      MillDependency(groupId, artifactId, version)
    }

  private def millDependencyRegex(dependency: Dependency): Regex = {
    val g = Regex.quote(dependency.groupId.value)
    val a = Regex.quote(dependency.artifactId.name)
    raw"""["`]$g:+$a:+(.*)["`;]""".r
  }

  private def findMavenDependency(
      dependency: Dependency,
      content: String
  ): Iterator[MavenDependency] =
    mavenDependencyRegex(dependency).findAllIn(content).matchData.map { m =>
      val groupId = SubstringPosition.fromMatch(m, dependency.groupId.value)
      val artifactId = SubstringPosition.fromMatch(m, dependency.artifactId.name)
      val version = SubstringPosition.fromMatch(m, m.group(1))
      MavenDependency(groupId, artifactId, version)
    }

  private def mavenDependencyRegex(dependency: Dependency): Regex = {
    val g = Regex.quote(dependency.groupId.value)
    val a = Regex.quote(dependency.artifactId.name)
    raw"""<groupId>$g</groupId>\s*<artifactId>$a</artifactId>\s*<version>(.*)</version>""".r
  }
}
