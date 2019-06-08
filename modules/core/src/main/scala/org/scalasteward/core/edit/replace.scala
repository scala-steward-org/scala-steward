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

object replace {
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
}
