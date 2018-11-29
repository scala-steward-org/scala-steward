/*
 * Copyright 2018 scala-steward contributors
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

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.MinSize
import eu.timepit.refined.refineV
import shapeless.Witness

object string {
  type MinLengthString[N] = String Refined MinSize[N]

  def longestCommonPrefix(s1: String, s2: String): String = {
    var i = 0
    val min = math.min(s1.length, s2.length)
    while (i < min && s1(i) == s2(i)) i = i + 1
    s1.substring(0, i)
  }

  def longestCommonPrefixGreater[N <: Int: Witness.Aux](
      xs: Nel[String]
  ): Option[MinLengthString[N]] =
    refineV[MinSize[N]](xs.reduceLeft(longestCommonPrefix)).toOption

  def removeSuffix(target: String, suffixes: List[String]): String =
    suffixes
      .find(suffix => target.endsWith(suffix))
      .fold(target)(suffix => target.substring(0, target.length - suffix.length))

  def lineLeftRight(s: String): String = {
    val line = "─" * 12
    s"$line $s $line"
  }
}
