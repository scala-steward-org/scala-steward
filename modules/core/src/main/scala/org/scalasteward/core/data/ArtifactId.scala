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

package org.scalasteward.core.data

import cats.Order
import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import monocle.Lens
import org.scalasteward.core.util.Nel

/** A name of an artifact as used in build files and with potential
  * cross version suffixes.
  *
  * @example {{{
  * scala> ArtifactId(
  *      |   name = "discipline-core",
  *      |   crossNames = List(
  *      |     "discipline-core_2.12",
  *      |     "discipline-core_2.13",
  *      |     "discipline-core_sjs0.6_2.12",
  *      |     "discipline-core_sjs0.6_2.13"
  *      | )).getClass.getSimpleName
  * res1: String = ArtifactId
  * }}}
  */
final case class ArtifactId(name: String, crossNames: List[String] = Nil) {
  def firstCrossName: String =
    crossNames.headOption.getOrElse(name)

  def names: Nel[String] =
    Nel(name, crossNames)

  def show: String =
    if (crossNames.isEmpty) name else names.mkString_("(", ", ", ")")
}

object ArtifactId {
  def apply(name: String, crossName: String): ArtifactId =
    ArtifactId(name, List(crossName))

  val crossName: Lens[ArtifactId, List[String]] =
    Lens[ArtifactId, List[String]](_.crossNames)(crossNames => _.copy(crossNames = crossNames))

  def combineCrossNames[A](lens: Lens[A, ArtifactId])(as: List[A]): List[A] = {
    val l = crossName.compose(lens)
    as.groupBy(l.set(Nil))
      .map { case (a, grouped) => l.set(grouped.flatMap(l.get).distinct.sorted)(a) }
      .toList
  }

  implicit val artifactIdCodec: Codec[ArtifactId] =
    deriveCodec

  implicit val artifactIdOrder: Order[ArtifactId] =
    Order.by((a: ArtifactId) => (a.name, a.crossNames))
}
