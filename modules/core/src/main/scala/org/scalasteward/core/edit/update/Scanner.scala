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

import cats.Monad
import cats.syntax.all._
import org.scalasteward.core.data.{Dependency, Update, Version}
import org.scalasteward.core.edit.update.Scanner.findVersionPositions
import org.scalasteward.core.edit.update.data.VersionPosition.{SbtModuleId, ScalaVal, Unclassified}
import org.scalasteward.core.edit.update.data.{FilePosition, VersionPosition}
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

final class Scanner[F[_]](implicit
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  def foo(repo: Repo, update: Update.Single) =
    gitAlg.findFilesContaining(repo, update.currentVersion.value).flatMap {
      _.traverse { file =>
        for {
          repoDir <- workspaceAlg.repoDir(repo)
          maybeContent <- fileAlg.readFile(repoDir / file)
          positions = maybeContent.toList.flatMap(findVersionPositions(update.dependencies, _))
        } yield file -> positions
      }
    }
}

object Scanner {

  // TODO:
  // - support scala-steward:on off

  def findVersionPositions(
      dependencies: Nel[Dependency],
      content: String
  ): List[VersionPosition] = {
    val version = dependencies.head.version
    val it = dependencies.toList.iterator.flatMap(findSbtModuleId(_, content)) ++
      findScalaVal(version, content) ++
      findUnclassified(version, content)
    it.distinctBy(_.filePosition).toList
  }

  def findVersionPositions(dependency: Dependency, content: String): List[VersionPosition] =
    findVersionPositions(Nel.one(dependency), content)

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

  private def findScalaVal(version: Version, content: String): Iterator[ScalaVal] =
    scalaValRegex(version).findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, version)
      val name = m.group(2)
      val before = m.group(1)
      ScalaVal(filePosition, name, before)
    }

  private def scalaValRegex(version: Version): Regex = {
    val ident = """[^=]+?"""
    val v = Regex.quote(version.value)
    raw"""(.*)val\s+($ident)\s*=\s*"$v"""".r
  }

  private def findUnclassified(version: Version, content: String): Iterator[Unclassified] = {
    val v = Regex.quote(version.value)
    val regex = raw"""(.*)$v""".r
    regex.findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, version)
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
