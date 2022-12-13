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

import cats.Foldable
import cats.syntax.all._
import java.util.regex.Pattern
import org.scalasteward.core.buildtool.mill.MillAlg.{isMillMainUpdate, millVersionName}
import org.scalasteward.core.buildtool.sbt.{buildPropertiesName, isSbtUpdate}
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.edit.update.data.VersionPosition._
import org.scalasteward.core.edit.update.data._
import org.scalasteward.core.scalafmt.{isScalafmtCoreUpdate, scalafmtConfName}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import scala.util.matching.Regex

object Selector {
  def select(
      update: Update.Single,
      versionPositions: PathList[List[VersionPosition]],
      modulePositions: PathList[List[ModulePosition]]
  ): PathList[List[Substring.Replacement]] = {
    val versionReplacements = firstNonEmpty(
      List(
        dependencyDefPositions(update.dependencies, versionPositions),
        scalaValInDependencyDefPositions(versionPositions, modulePositions),
        millVersionPositions(update, versionPositions),
        sbtVersionPositions(update, versionPositions),
        scalafmtVersionPositions(update, versionPositions)
      ).flatten,
      matchingSearchTerms(heuristic1SearchTerms(update), versionPositions),
      matchingSearchTerms(heuristic2SearchTerms(update), versionPositions),
      matchingSearchTerms(heuristic3SearchTerms(update), versionPositions),
      matchingSearchTerms(heuristic4SearchTerms(update), versionPositions),
      matchingSearchTerms(heuristic5SearchTerms(update), versionPositions)
    ).map { case (path, positions) =>
      path -> positions.map(_.version.replaceWith(update.nextVersion.value))
    }

    (versionReplacements ++ moduleReplacements(update, modulePositions))
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.flatten)
      .toList
  }

  private def dependencyDefPositions(
      dependencies: Nel[Dependency],
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[DependencyDef]] =
    mapFilterNonEmpty(versionPositions) { case (_, positions) =>
      positions
        .collect {
          case p: MillDependency if scalaCliUsingLib.matcher(p.before).matches() => p
          case p: DependencyDef if !p.isCommented                                => p
        }
        .filterNot {
          case p: SbtDependency => p.before.toLowerCase.contains("previous")
          case _                => false
        }
        .filter { p =>
          dependencies.exists { d =>
            d.groupId.value === p.groupId && d.artifactId.names.contains_(p.artifactId)
          }
        }
    }

  private def scalaCliUsingLib: Pattern =
    Pattern.compile("""//>\s+using\s+lib\s+""")

  private def scalaValInDependencyDefPositions(
      versionPositions: PathList[List[VersionPosition]],
      modulePositions: PathList[List[ModulePosition]]
  ): PathList[List[ScalaVal]] =
    mapFilterNonEmpty(scalaValPositions(versionPositions)) { case (path, positions) =>
      positions.filter { p =>
        modulePositions.exists { case (path2, positions2) =>
          path === path2 && positions2.exists { p2 =>
            val unwrapped = p2.unwrappedVersion
            unwrapped === p.name || unwrapped.endsWith("." + p.name)
          }
        }
      }
    }

  private def scalaValPositions(
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[ScalaVal]] =
    mapFilterNonEmpty(versionPositions) { case (_, positions) =>
      positions.collect {
        case p: ScalaVal if genericScalaValFilter(p) => p
      }
    }

  private def genericScalaValFilter(p: ScalaVal): Boolean =
    !p.isCommented && !p.name.toLowerCase.startsWith("previous")

  private def matchingSearchTerms(
      searchTerms: List[String],
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[VersionPosition]] =
    searchTermsAsRegex(searchTerms)
      .map { pattern =>
        mapFilterNonEmpty(versionPositions) { case (_, positions) =>
          positions.collect {
            case p: ScalaVal if genericScalaValFilter(p) && pattern.matcher(p.name).find() => p
            case p: Unclassified if pattern.matcher(p.before).find()                       => p
          }
        }
      }
      .getOrElse(List.empty)

  private def heuristic1SearchTerms(update: Update.Single): List[String] = {
    val terms = update match {
      case s: Update.ForArtifactId => List(s.artifactId.name)
      case g: Update.ForGroupId =>
        g.artifactIds.map(_.name).toList ++ g.artifactIdsPrefix.map(_.value).toList
    }
    terms.map(Update.nameOf(update.groupId, _))
  }

  private def heuristic2SearchTerms(update: Update.Single): List[String] =
    util.string.extractWords(update.mainArtifactId).filterNot(isCommonWord)

  private def heuristic3SearchTerms(update: Update.Single): List[String] =
    update.mainArtifactId.toSeq.sliding(5).map(_.unwrap).filterNot(isCommonWord).toList

  private def heuristic4SearchTerms(update: Update.Single): List[String] =
    List(update.groupId.value)

  private def heuristic5SearchTerms(update: Update.Single): List[String] =
    update.groupId.value
      .split('.')
      .toList
      .drop(1)
      .flatMap(util.string.extractWords)
      .filter(_.length > 3)

  private def searchTermsAsRegex(terms: List[String]): Option[Pattern] =
    Nel
      .fromList(terms.map(removeCommonSuffix).map(toFlexibleRegex).filter(_.nonEmpty))
      .map(ts => Pattern.compile("(?i)" + alternation(ts)))

  private def removeCommonSuffix(str: String): String =
    util.string.removeSuffix(str, Update.commonSuffixes)

  /** Removes punctuation from the input and returns it as regex that allows
    * punctuation between characters.
    */
  private def toFlexibleRegex(string: String): String = {
    val punctuation = List('.', '-', '_')
    val allowPunctuation = "[\\.\\-_]*"
    string.toList
      .collect { case c if !punctuation.contains_(c) => Regex.quote(c.toString) }
      .intercalate(allowPunctuation)
  }

  private def alternation[F[_]: Foldable](strings: F[String]): String =
    strings.mkString_("(", "|", ")")

  private def isCommonWord(s: String): Boolean =
    s === "scala"

  private def millVersionPositions(
      update: Update.Single,
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[VersionPosition]] =
    if (isMillMainUpdate(update))
      mapFilterNonEmpty(versionPositions) { case (path, positions) =>
        if (path === millVersionName) positions else List.empty
      }
    else List.empty

  private def sbtVersionPositions(
      update: Update.Single,
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[VersionPosition]] =
    if (isSbtUpdate(update))
      mapFilterNonEmpty(matchingSearchTerms(List("sbt.version"), versionPositions)) {
        case (path, positions) => if (path.endsWith(buildPropertiesName)) positions else List.empty
      }
    else List.empty

  private def scalafmtVersionPositions(
      update: Update.Single,
      versionPositions: PathList[List[VersionPosition]]
  ): PathList[List[VersionPosition]] =
    if (isScalafmtCoreUpdate(update))
      mapFilterNonEmpty(matchingSearchTerms(List("version"), versionPositions)) {
        case (path, positions) => if (path === scalafmtConfName) positions else List.empty
      }
    else List.empty

  private def moduleReplacements(
      update: Update.Single,
      modulePositions: PathList[List[ModulePosition]]
  ): PathList[List[Substring.Replacement]] = {
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
            currentArtifactId.names.contains_(p.artifactId.value)
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
