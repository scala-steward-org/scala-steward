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

package eu.timepit.scalasteward.update

import eu.timepit.scalasteward.dependency.Dependency

object splitter {
  // WIP
  def xxx(dependencies: List[Dependency]): List[List[Dependency]] = {

    val (duplicated, distinct) =
      dependencies.groupBy(d => (d.groupId, d.artifactIdCross)).values.partition(_.size > 1)

    val buckets = distinct.grouped(20).map(_.flatten.toList).toList

    val x = transpose(duplicated.toList).zipAll(buckets, List.empty, List.empty)
    x.map(t => t._1 ++ t._2)
  }

  // https://stackoverflow.com/a/1684814/460387
  def transpose[A](xs: List[List[A]]): List[List[A]] = xs.filter(_.nonEmpty) match {
    case Nil               => Nil
    case ys: List[List[A]] => ys.map { _.head } :: transpose(ys.map { _.tail })
  }

}
