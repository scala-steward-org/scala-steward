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

package org.scalasteward.core.edit

import scala.collection.mutable

package object update {
  def isInside(index: Int, regions: List[(Int, Int)]): Boolean =
    regions.exists { case (start, end) => start <= index && index <= end }

  private val offMarker = "scala-steward:off"
  private val onMarker = "scala-steward:on"
  private val regexIgnoreMultiLinesBegins = raw"""\s*\p{Punct}+\s*$offMarker""".r

  /** Find index regions in the string `s` that are surrounded by scala-steward:off and
    * scala-steward:on markers.
    */
  def findOffRegions(s: String): List[(Int, Int)] = {
    var off = false
    var start = 0
    var end = 0
    val buffer = mutable.ListBuffer.empty[(Int, Int)]
    s.linesWithSeparators.foreach { line =>
      if (off) {
        if (line.contains(onMarker)) {
          buffer.addOne((start, end + line.indexOf(onMarker)))
          off = false
          start = end + line.length
          end = start
        } else {
          end = end + line.length
        }
      } else {
        if (line.contains(offMarker)) {
          if (regexIgnoreMultiLinesBegins.findPrefixOf(line).isDefined) { // region off
            off = true
            end = end + line.length
          } else { // single line off
            buffer.addOne((start, start + line.indexOf(offMarker)))
            start = start + line.length
            end = start
          }
        } else {
          start = start + line.length
          end = start
        }
      }
    }
    if (end > start) buffer.addOne((start, end))
    buffer.toList
  }
}
