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

package eu.timepit.scalasteward

import cats.Monad
import cats.data.NonEmptyList
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString

object util {
  def ifTrue[F[_]: Monad](fb: F[Boolean])(f: F[Unit]): F[Unit] =
    fb.ifM(f, Monad[F].unit)

  def longestCommonPrefix(s1: String, s2: String): String = {
    var i = 0
    val min = math.min(s1.length, s2.length)
    while (i < min && s1(i) == s2(i)) i = i + 1
    s1.substring(0, i)
  }

  def longestCommonPrefix(xs: NonEmptyList[String]): String =
    xs.reduceLeft(longestCommonPrefix)

  def longestCommonNonEmptyPrefix(xs: NonEmptyList[String]): Option[NonEmptyString] =
    NonEmptyString.unapply(longestCommonPrefix(xs))

  def removeSuffix(target: String, suffixes: List[String]): String =
    suffixes
      .find(suffix => target.endsWith(suffix))
      .fold(target)(suffix => target.substring(0, target.length - suffix.length))
}
