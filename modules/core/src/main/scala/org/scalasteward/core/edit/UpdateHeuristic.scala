/*
 * Copyright 2018-2019 scala-steward contributors
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

import org.scalasteward.core.model.Update
import org.scalasteward.core.util
import scala.util.matching.Regex

final case class UpdateHeuristic(
    name: String,
    order: Int,
    getSearchTerms: Update => List[String],
    getPrefixRegex: Update => Option[String] = _ => None
) {
  def mkRegex(update: Update): Option[Regex] =
    UpdateHeuristic
      .searchTermsToAlternation(getSearchTerms(update).map(Update.removeCommonSuffix))
      .map { searchTerms =>
        val prefix = getPrefixRegex(update).getOrElse("")
        val currentVersion = Regex.quote(update.currentVersion)
        s"(?i)(.*)($prefix$searchTerms.*?)$currentVersion".r
      }

  def replaceF(update: Update): String => Option[String] =
    mkRegex(update).fold((_: String) => Option.empty[String]) { regex => target =>
      util.string.replaceSomeInOpt(
        regex,
        target,
        match0 => {
          val group1 = match0.group(1)
          val group2 = match0.group(2)
          if (group1.toLowerCase.contains("previous") || group1.trim.startsWith("//"))
            None
          else
            Some(group1 + group2 + update.nextVersion)
        }
      )
    }
}

object UpdateHeuristic {
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

  val strict = UpdateHeuristic(
    name = "strict",
    order = 1,
    getSearchTerms = update => update.searchTerms.toList,
    getPrefixRegex = update => Some(s"${update.groupId}.*?")
  )

  val original = UpdateHeuristic(
    name = "original",
    order = 2,
    getSearchTerms = update => update.searchTerms.toList
  )

  val relaxed = UpdateHeuristic(
    name = "relaxed",
    order = 3,
    getSearchTerms = update => util.string.extractWords(update.artifactId)
  )

  val sliding = UpdateHeuristic(
    name = "sliding",
    order = 4,
    getSearchTerms = update => update.artifactId.sliding(5).take(5).toList
  )

  val groupId = UpdateHeuristic(
    name = "groupId",
    order = 5,
    getSearchTerms = update =>
      update.groupId
        .split('.')
        .toList
        .drop(1)
        .flatMap(util.string.extractWords)
        .filter(_.length > 3)
  )
}
