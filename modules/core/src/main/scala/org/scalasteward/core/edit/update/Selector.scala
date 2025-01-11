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

import cats.Foldable
import cats.syntax.all.*
import java.util.regex.Pattern
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.{buildPropertiesName, isSbtUpdate}
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.edit.update.data.*
import org.scalasteward.core.edit.update.data.VersionPosition.*
import org.scalasteward.core.scalafmt.{isScalafmtCoreUpdate, scalafmtConfName}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import scala.util.matching.Regex

/** This object is responsible for selecting the "correct" version positions where the version
  * should be updated and module positions where the groupId and/or artifactId should be updated in
  * case of artifact migrations. These positions are then turned into [[data.Substring.Replacement]]
  * that are used in [[org.scalasteward.core.edit.EditAlg]] to actually edit files in the repo.
  */
object Selector {
  def select(
      update: Update.Single,
      versionPositions: List[VersionPosition],
      modulePositions: List[ModulePosition]
  ): List[Substring.Replacement] = {
    val matchingVersionPositions =
      List(
        dependencyDefPositions(update.dependencies, versionPositions),
        scalaValInDependencyDefPositions(versionPositions, modulePositions),
        millVersionPositions(update, versionPositions),
        sbtVersionPositions(update, versionPositions),
        scalafmtVersionPositions(update, versionPositions)
      ).flatten #::
        matchingSearchTerms(heuristic1SearchTerms(update), versionPositions) #::
        matchingSearchTerms(heuristic2SearchTerms(update), versionPositions) #::
        matchingSearchTerms(heuristic3SearchTerms(update), versionPositions) #::
        matchingSearchTerms(heuristic4SearchTerms(update), versionPositions) #::
        matchingSearchTerms(heuristic5SearchTerms(update), versionPositions) #::
        LazyList.empty

    val versionReplacements = matchingVersionPositions
      .find(_.nonEmpty)
      .getOrElse(List.empty)
      .map(_.version.replaceWith(update.nextVersion.value))

    versionReplacements ++ moduleReplacements(update, modulePositions)
  }

  private def dependencyDefPositions(
      dependencies: Nel[Dependency],
      versionPositions: List[VersionPosition]
  ): List[DependencyDef] =
    versionPositions
      .collect { case p: DependencyDef => p }
      .filter {
        case p: MillDependency =>
          scalaCliUsingLib.matcher(p.before).matches() ||
          scalaCliUsingDep.matcher(p.before).matches() ||
          scalaCliUsingTestDep.matcher(p.before).matches() ||
          !p.isCommented
        case p: SbtDependency => !p.isCommented && !p.before.toLowerCase.contains("previous")
        case _                => true
      }
      .filter { p =>
        val artifactIdNames = Set(p.artifactId, p.artifactId.takeWhile(_ =!= '_'))
        dependencies.exists { d =>
          d.groupId.value === p.groupId && d.artifactId.names.exists(artifactIdNames)
        }
      }

  private val scalaCliUsingLib: Pattern =
    Pattern.compile("""//>\s+using\s+lib\s+""")

  private val scalaCliUsingDep: Pattern =
    Pattern.compile("""//>\s+using\s+dep\s+""")

  private val scalaCliUsingTestDep: Pattern =
    Pattern.compile("""//>\s+using\s+test\.dep\s+""")

  private def scalaValInDependencyDefPositions(
      versionPositions: List[VersionPosition],
      modulePositions: List[ModulePosition]
  ): List[ScalaVal] =
    scalaValPositions(versionPositions).filter { vp =>
      modulePositions.exists { mp =>
        val unwrapped = mp.unwrappedVersion
        unwrapped === vp.name || unwrapped.endsWith("." + vp.name)
      }
    }

  private def scalaValPositions(versionPositions: List[VersionPosition]): List[ScalaVal] =
    versionPositions.collect {
      case p: ScalaVal if genericScalaValFilter(p) => p
    }

  private def genericScalaValFilter(p: ScalaVal): Boolean =
    !p.isCommented && !p.name.toLowerCase.startsWith("previous")

  private def matchingSearchTerms(
      searchTerms: List[String],
      versionPositions: List[VersionPosition]
  ): List[VersionPosition] =
    searchTermsAsRegex(searchTerms).fold(List.empty[VersionPosition]) { pattern =>
      versionPositions.collect {
        case p: ScalaVal if genericScalaValFilter(p) && pattern.matcher(p.name).find() => p
        case p: Unclassified if pattern.matcher(p.before).find()                       => p
      }
    }

  private def heuristic1SearchTerms(update: Update.Single): List[String] = {
    val terms = update match {
      case s: Update.ForArtifactId => List(s.artifactId.name)
      case g: Update.ForGroupId    => g.artifactIds.map(_.name).toList ++ g.artifactIdsPrefix.toList
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

  /** Removes punctuation from the input and returns it as regex that allows punctuation between
    * characters.
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
      versionPositions: List[VersionPosition]
  ): List[VersionPosition] =
    if (MillAlg.isMillMainUpdate(update))
      versionPositions.filter(_.version.path.endsWith(MillAlg.millVersionName))
    else List.empty

  private def sbtVersionPositions(
      update: Update.Single,
      versionPositions: List[VersionPosition]
  ): List[VersionPosition] =
    if (isSbtUpdate(update))
      matchingSearchTerms(List("sbt.version"), versionPositions)
        .filter(_.version.path.endsWith(buildPropertiesName))
    else List.empty

  private def scalafmtVersionPositions(
      update: Update.Single,
      versionPositions: List[VersionPosition]
  ): List[VersionPosition] =
    if (isScalafmtCoreUpdate(update))
      matchingSearchTerms(List("version"), versionPositions)
        .filter(_.version.path.endsWith(scalafmtConfName))
    else List.empty

  private def moduleReplacements(
      update: Update.Single,
      modulePositions: List[ModulePosition]
  ): List[Substring.Replacement] =
    update.forArtifactIds.toList.flatMap { forArtifactId =>
      val newerGroupId = forArtifactId.newerGroupId
      val newerArtifactId = forArtifactId.newerArtifactId
      if (newerGroupId.isEmpty && newerArtifactId.isEmpty) List.empty
      else {
        val currentGroupId = forArtifactId.groupId
        val currentArtifactId = forArtifactId.artifactIds.head
        modulePositions
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
