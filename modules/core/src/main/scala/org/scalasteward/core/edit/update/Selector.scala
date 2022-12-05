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

import org.scalasteward.core.edit.update.data.VersionPosition._
import org.scalasteward.core.edit.update.data.{PathList, UpdatePositions, VersionPosition}

object Selector {
  def select(positions: UpdatePositions): UpdatePositions =
    UpdatePositions(
      versionPositions = firstNonEmpty(
        dependencyDefPositions(positions.versionPositions),
        scalaValPositions(positions.versionPositions),
        unclassifiedPositions(positions.versionPositions)
      ),
      modulePositions = List.empty
    )

  private def firstNonEmpty[A](lists: List[A]*): List[A] =
    lists.find(_.nonEmpty).getOrElse(List.empty)

  private def dependencyDefPositions(
      positionsByPath: PathList[List[VersionPosition]]
  ): PathList[List[VersionPosition]] =
    positionsByPath
      .map { case (path, positions) =>
        path -> positions.collect {
          case p: SbtModuleId if !p.isCommented    => p
          case p: MillDependency if !p.isCommented => p
        }
      }
      .filter { case (_, positions) => positions.nonEmpty }

  private def scalaValPositions(
      positionsByPath: PathList[List[VersionPosition]]
  ): PathList[List[ScalaVal]] =
    positionsByPath
      .map { case (path, positions) =>
        path -> positions.collect {
          case p: ScalaVal if !p.isCommented && !p.name.startsWith("previous") => p
        }
      }
      .filter { case (_, positions) => positions.nonEmpty }

  private def unclassifiedPositions(
      positionsByPath: PathList[List[VersionPosition]]
  ): PathList[List[Unclassified]] =
    positionsByPath
      .map { case (path, positions) =>
        path -> positions.collect { case p: Unclassified => p }
      }
      .filter { case (_, positions) => positions.nonEmpty }
}
