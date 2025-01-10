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

package org.scalasteward.core.data

import cats.Order
import cats.syntax.all.*
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.util.Nel

/** A list of dependencies with the same groupId, (non-cross) artifactId, and version. */
final case class CrossDependency(dependencies: Nel[Dependency]) {
  def head: Dependency =
    dependencies.head

  def showArtifactNames: String = {
    val names = dependencies.flatMap(_.artifactId.names).distinct.sorted
    if (names.tail.isEmpty) names.head else names.mkString_("(", ", ", ")")
  }
}

object CrossDependency {
  def apply(dependency: Dependency): CrossDependency =
    CrossDependency(Nel.one(dependency))

  def group(dependencies: List[Dependency]): List[CrossDependency] =
    dependencies
      .groupByNel(d => (d.groupId, d.artifactId.name, d.version))
      .values
      .map(grouped => CrossDependency(grouped.sorted))
      .toList

  implicit val crossDependencyDecoder: Decoder[CrossDependency] =
    Decoder[Nel[Dependency]].map(CrossDependency.apply)

  implicit val crossDependencyEncoder: Encoder[CrossDependency] =
    Encoder[Nel[Dependency]].contramap(_.dependencies)

  implicit val crossDependencyOrder: Order[CrossDependency] =
    Order.by(_.dependencies)
}
