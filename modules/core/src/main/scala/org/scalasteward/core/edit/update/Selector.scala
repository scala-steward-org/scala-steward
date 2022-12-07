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

import cats.syntax.all._
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.edit.update.data.VersionPosition._
import org.scalasteward.core.edit.update.data._
import org.scalasteward.core.util.Nel

object Selector {
  def select(
      update: Update.Single,
      versionPositions: PathList[List[VersionPosition]],
      modulePositions: PathList[List[ModulePosition]]
  ): PathList[List[SubstringReplacement]] = {
    val versionReplacements = firstNonEmpty(
      dependencyDefPositions(update.dependencies, versionPositions) ++
        scalaValInDependencyDefPositions(versionPositions, modulePositions),
      scalaValPositions(versionPositions),
      unclassifiedPositions(versionPositions)
    ).map { case (path, positions) =>
      path -> positions.map(_.version.replaceWith(update.nextVersion.value))
    }

    versionReplacements ++ moduleReplacements(update, modulePositions)
  }

  private def dependencyDefPositions(
      dependencies: Nel[Dependency],
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[DependencyDef]] =
    mapFilterNonEmpty(versionPositions) { case (_, positions) =>
      positions
        .collect { case p: DependencyDef if !p.isCommented => p }
        .filter { p =>
          dependencies.exists { d =>
            d.groupId.value === p.groupId && d.artifactId.name === p.artifactId
          }
        }
    }

  private def scalaValInDependencyDefPositions(
      versionPositions: PathList[List[VersionPosition]],
      modulePositions: PathList[List[ModulePosition]]
  ): PathList[List[ScalaVal]] =
    mapFilterNonEmpty(scalaValPositions(versionPositions)) { case (path, positions) =>
      positions.filter { p =>
        modulePositions.exists { case (path2, positions2) =>
          path === path2 && positions2.exists { p2 =>
            p2.version.value === p.name || p2.version.value.endsWith("." + p.name)
          }
        }
      }
    }

  private def scalaValPositions(
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[ScalaVal]] =
    mapFilterNonEmpty(versionPositions) { case (_, positions) =>
      positions.collect {
        case p: ScalaVal if !p.isCommented && !p.name.startsWith("previous") => p
      }
    }

  private def unclassifiedPositions(
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[Unclassified]] =
    mapFilterNonEmpty(versionPositions) { case (_, positions) =>
      positions.collect { case p: Unclassified => p }
    }

  private def moduleReplacements(
      update: Update.Single,
      modulePositions: PathList[List[ModulePosition]]
  ): PathList[List[SubstringReplacement]] = {
    val (newerGroupId, newerArtifactId) = update match {
      case u: Update.ForArtifactId => (u.newerGroupId, u.newerArtifactId)
      case _: Update.ForGroupId    => (None, None)
    }
    if (newerGroupId.isEmpty && newerArtifactId.isEmpty) List.empty
    else {
      val currentGroupId = update.groupId
      val currentArtifactId = update.artifactIds.head
      mapFilterNonEmpty(modulePositions) { case (_, positions) =>
        positions
          .filter { p =>
            p.groupId.value === currentGroupId.value &&
            p.artifactId.value === currentArtifactId.name
          }
          .flatMap { p =>
            newerGroupId.map(g => p.groupId.replaceWith(g.value)).toList ++
              newerArtifactId.map(a => p.artifactId.replaceWith(a)).toList
          }
      }
    }
  }

  private def firstNonEmpty[A](lists: List[A]*): List[A] =
    lists.find(_.nonEmpty).getOrElse(List.empty)

  private def mapFilterNonEmpty[A, B](pathList: PathList[List[A]])(
      f: (String, List[A]) => List[B]
  ): PathList[List[B]] =
    pathList.map { case (path, as) => path -> f(path, as) }.filter { case (_, bs) => bs.nonEmpty }
}
