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

package org.scalasteward.core.scan

import org.scalasteward.core.data.Dependency
import org.scalasteward.core.edit.VersionPosition
import org.scalasteward.core.edit.VersionPosition.{SbtModuleId, ScalaVal, Unclassified}
import org.scalasteward.core.io.FilePosition
import scala.util.matching.Regex

object scanner {

  // support scala-steward:on,off

  def findVersionPositions(dependency: Dependency)(content: String): List[VersionPosition] = {
    val it = findSbtModuleId(dependency)(content) ++
      findScalaVal(dependency)(content) ++
      findUnclassified(dependency)(content)
    it.distinctBy(_.filePosition.index).toList
  }

  private def findSbtModuleId(dependency: Dependency)(content: String): Iterator[SbtModuleId] =
    sbtModuleIdRegex(dependency).findAllIn(content).matchData.map { m =>
      val index = m.start + m.matched.indexOf(dependency.version.value)
      val filePosition = filePositionFrom(content, index)
      SbtModuleId(filePosition)
    }

  private def sbtModuleIdRegex(dependency: Dependency): Regex = {
    val g = Regex.quote(dependency.groupId.value)
    val a = Regex.quote(dependency.artifactId.name)
    val v = Regex.quote(dependency.version.value)
    raw""""$g"\s*%{1,3}\s*"$a"\s*%\s*"$v"""".r
  }

  private def findScalaVal(dependency: Dependency)(content: String): Iterator[ScalaVal] =
    scalaValRegex(dependency).findAllIn(content).matchData.map { m =>
      val index = m.start + m.matched.indexOf(dependency.version.value)
      val filePosition = filePositionFrom(content, index)
      val name = m.group(2)
      val before = m.group(1)
      ScalaVal(filePosition, name, before)
    }

  private def scalaValRegex(dependency: Dependency): Regex = {
    val ident = """[^=]+?"""
    val v = Regex.quote(dependency.version.value)
    raw"""(.*)val\s+($ident)\s*=\s*"$v"""".r
  }

  private def findUnclassified(dependency: Dependency)(content: String): Iterator[Unclassified] = {
    val v = Regex.quote(dependency.version.value)
    val regex = raw"""$v""".r
    regex.findAllIn(content).matchData.map { m =>
      val index = m.start + m.matched.indexOf(dependency.version.value)
      val filePosition = filePositionFrom(content, index)
      Unclassified(filePosition)
    }
  }

  private def filePositionFrom(s: String, index: Int): FilePosition = {
    var c = 0
    var line = 1
    var column = 0
    while (c <= index) {
      if (s.charAt(c) == '\n') {
        line = line + 1
        column = 0
      } else {
        column = column + 1
      }
      c = c + 1
    }
    FilePosition(index, line, column)
  }
}
