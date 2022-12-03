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

package org.scalasteward.core.edit

import org.scalasteward.core.data.{Dependency, Version}
import org.scalasteward.core.edit.VersionPosition.{SbtModuleId, ScalaVal, Unclassified}
import org.scalasteward.core.io.FilePosition
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

object VersionScanner {

  // TODO:
  // - support scala-steward:on off

  def findVersionPositions(dependency: Dependency, content: String): List[VersionPosition] = {
    val it = findSbtModuleId(dependency, content) ++
      findScalaVal(dependency, content) ++
      findUnclassified(dependency, content)
    it.distinctBy(_.filePosition.start).toList
  }

  private def findSbtModuleId(dependency: Dependency, content: String): Iterator[SbtModuleId] =
    sbtModuleIdRegex(dependency).findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, dependency.version)
      val before = m.group(1)
      SbtModuleId(filePosition, before)
    }

  private def sbtModuleIdRegex(dependency: Dependency): Regex = {
    val g = Regex.quote(dependency.groupId.value)
    val a = Regex.quote(dependency.artifactId.name)
    val v = Regex.quote(dependency.version.value)
    raw"""(.*)"$g"\s*%{1,3}\s*"$a"\s*%\s*"$v"""".r
  }

  private def findScalaVal(dependency: Dependency, content: String): Iterator[ScalaVal] =
    scalaValRegex(dependency).findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, dependency.version)
      val name = m.group(2)
      val before = m.group(1)
      ScalaVal(filePosition, name, before)
    }

  private def scalaValRegex(dependency: Dependency): Regex = {
    val ident = """[^=]+?"""
    val v = Regex.quote(dependency.version.value)
    raw"""(.*)val\s+($ident)\s*=\s*"$v"""".r
  }

  private def findUnclassified(dependency: Dependency, content: String): Iterator[Unclassified] = {
    val v = Regex.quote(dependency.version.value)
    val regex = raw"""(.*)$v""".r
    regex.findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, dependency.version)
      val before = m.group(1)
      Unclassified(filePosition, before)
    }
  }

  private def filePositionFrom(m: Match, version: Version): FilePosition = {
    val start = m.start + m.matched.indexOf(version.value)
    val end = start + version.value.length
    FilePosition(start, end)
  }
}
