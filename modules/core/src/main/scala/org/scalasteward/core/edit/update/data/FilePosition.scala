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

package org.scalasteward.core.edit.update.data

import scala.util.matching.Regex.Match

final case class FilePosition(start: Int, value: String) {
  def replaceIn(source: String, replacement: String): String =
    source.substring(0, start) + replacement + source.substring(start + value.length)
}

object FilePosition {
  def fromMatch(m: Match, value: String): FilePosition = {
    val start = m.start + m.matched.indexOf(value)
    FilePosition(start, value)
  }
}
