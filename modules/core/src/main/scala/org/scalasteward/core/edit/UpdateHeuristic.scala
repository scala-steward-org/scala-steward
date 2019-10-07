/*
 * Copyright 2018-2019 Scala Steward contributors
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

import cats.implicits._
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel
import scala.util.matching.Regex

/** `UpdateHeuristic` is a wrapper for a function that takes an `Update` and
  * returns a new function that replaces the current version with the next
  * version in its input. The result type indicates whether the input was
  * modified or not.
  */
final case class UpdateHeuristic(
    name: String,
    replaceVersion: Update => String => Option[String]
)

object UpdateHeuristic {
  private def defaultReplaceVersion(
      getSearchTerms: Update => List[String],
      getPrefixRegex: Update => Option[String] = _ => None
  ): Update => String => Option[String] = {

    def searchTermsToAlternation(terms: List[String]): Option[String] = {
      val ignoreChar = ".?"
      val ignorableStrings = List(".", "-")
      val terms1 = terms
        .filterNot(term => term.isEmpty || ignorableStrings.contains(term))
        .map { term =>
          ignorableStrings.foldLeft(term) {
            case (term1, ignorable) => term1.replace(ignorable, ignoreChar)
          }
        }

      if (terms1.nonEmpty) Some(terms1.mkString("(", "|", ")")) else None
    }

    def mkRegex(update: Update): Option[Regex] =
      searchTermsToAlternation(getSearchTerms(update).map(Update.removeCommonSuffix)).map {
        searchTerms =>
          val prefix = getPrefixRegex(update).getOrElse("")
          val currentVersion = Regex.quote(update.currentVersion)
          s"(?i)(.*)($prefix$searchTerms.*?)$currentVersion(.?)".r
      }

    def replaceF(update: Update): String => Option[String] =
      target =>
        for {
          s <- replaceVersionF(update)(target)
          s <- replaceGroupF(update)(s)
        } yield s

    def replaceVersionF(update: Update): String => Option[String] =
      mkRegex(update).fold((_: String) => Option.empty[String]) { regex => target =>
        replaceSomeInAllowedParts(
          regex,
          target,
          match0 => {
            val group1 = match0.group(1)
            val group2 = match0.group(2)
            val lastGroup = match0.group(match0.groupCount)
            val versionInQuotes =
              group2.lastOption.filter(_ === '"').fold(true)(lastGroup.headOption.contains_)
            if (group1.toLowerCase.contains("previous")
                || group1.trim.startsWith("//")
                || !versionInQuotes) None
            else Some(Regex.quoteReplacement(group1 + group2 + update.nextVersion + lastGroup))
          }
        ).someIfChanged
      }

    def replaceGroupF(update: Update): String => Option[String] = { target =>
      update match {
        case Update.Single(groupId, artifactId, _, _, _, Some(newerGroupId)) =>
          val currentGroupId = Regex.quote(groupId.value)
          val currentArtifactId = Regex.quote(artifactId)
          val regex = s"""(?i)(.*)${currentGroupId}(.*${currentArtifactId})""".r
          replaceSomeInAllowedParts(regex, target, match0 => {
            val group1 = match0.group(1)
            val group2 = match0.group(2)
            Some(s"""$group1$newerGroupId$group2""")
          }).someIfChanged
        case _ => Some(target)
      }
    }

    replaceF
  }

  val strict = UpdateHeuristic(
    name = "strict",
    replaceVersion =
      defaultReplaceVersion(_.searchTerms.toList, update => Some(s"${update.groupId}.*?"))
  )

  val original = UpdateHeuristic(
    name = "original",
    replaceVersion = defaultReplaceVersion(_.searchTerms.toList)
  )

  val relaxed = UpdateHeuristic(
    name = "relaxed",
    replaceVersion = defaultReplaceVersion(update => util.string.extractWords(update.artifactId))
  )

  val sliding = UpdateHeuristic(
    name = "sliding",
    replaceVersion =
      defaultReplaceVersion(_.artifactId.sliding(5).take(5).filterNot(_ === "scala").toList)
  )

  val groupId = UpdateHeuristic(
    name = "groupId",
    replaceVersion = defaultReplaceVersion(
      _.groupId.value
        .split('.')
        .toList
        .drop(1)
        .flatMap(util.string.extractWords)
        .filter(_.length > 3)
    )
  )

  val specific = UpdateHeuristic(
    name = "specific",
    replaceVersion = defaultReplaceVersion {
      case Update.Single(GroupId("org.scalameta"), "scalafmt-core", _, _, _, _) => List("version")
      case _                                                                    => List.empty
    }
  )

  val all: Nel[UpdateHeuristic] =
    Nel.of(strict, original, relaxed, sliding, groupId, specific)
}
