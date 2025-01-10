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

package org.scalasteward.core.util

import cats.Foldable
import cats.syntax.all.*
import scala.util.matching.Regex

object string {

  /** Extracts words from a string.
    *
    * Words are separated by '-', '_', '.', or a change from lower to upper case and are at least
    * three characters long.
    */
  def extractWords(s: String): List[String] = {
    val minLength = 3
    val splitBySeparators = s.split(Array('-', '_', '.')).toList
    splitBySeparators
      .flatMap(splitBetweenLowerAndUpperChars)
      .filter(_.length >= minLength)
  }

  def indentLines[F[_]: Foldable](fs: F[String]): String = {
    val indent = "  "
    val delim = "\n" + indent
    fs.foldSmash(indent, delim, "")
  }

  def longestCommonPrefix(s1: String, s2: String): String = {
    var i = 0
    val min = math.min(s1.length, s2.length)
    while (i < min && s1(i) === s2(i)) i = i + 1
    s1.substring(0, i)
  }

  def longestCommonPrefixGteq(xs: Nel[String], n: Int): Option[String] = {
    val prefix = xs.reduceLeft(longestCommonPrefix)
    Option.when(prefix.length >= n)(prefix)
  }

  def removeSuffix(target: String, suffixes: List[String]): String =
    suffixes
      .find(suffix => target.endsWith(suffix))
      .fold(target)(suffix => target.substring(0, target.length - suffix.length))

  /** Returns the substring after the rightmost `.`.
    *
    * @example
    *   {{{
    *   scala> string.rightmostLabel("org.scalasteward.core")
    *   res1: String = core
    *   }}}
    */
  def rightmostLabel(s: String): String =
    s.split('.').lastOption.getOrElse(s)

  def lineLeftRight(s: String): String = {
    val line = "─" * 12
    s"$line $s $line"
  }

  private val lowerUpperRegex = "\\p{javaLowerCase}\\p{javaUpperCase}".r

  /** Splits a string between lower and upper case characters.
    *
    * @example
    *   {{{
    *   scala> string.splitBetweenLowerAndUpperChars("javaLowerCase")
    *   res1: List[String] = List(java, Lower, Case)
    *
    *   scala> string.splitBetweenLowerAndUpperChars("HikariCP")
    *   res2: List[String] = List(Hikari, CP)
    *   }}}
    */
  def splitBetweenLowerAndUpperChars(s: String): List[String] =
    splitBetween2CharMatches(lowerUpperRegex)(s)

  private def splitBetween2CharMatches(regex: Regex)(s: String): List[String] = {
    val bounds = regex.findAllIn(s).matchData.map(_.start + 1).toList
    val indices = 0 +: bounds :+ s.length
    indices.sliding(2).collect { case i1 :: i2 :: Nil => s.substring(i1, i2) }.toList
  }
}
