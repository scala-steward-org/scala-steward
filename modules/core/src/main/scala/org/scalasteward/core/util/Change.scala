/*
 * Copyright 2018-2020 Scala Steward contributors
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

import cats.{Eq, Monoid}
import org.scalasteward.core.util.Change.Changed

/** Data type that indicates whether some value of type `T` has been changed
  * or not via its two constructors `Changed` and `Unchanged`.
  *
  * It is isomorphic to `(T, Boolean)`.
  *
  * `Change[T]` is a monoid for any monoid `T` with `Unchanged(T.empty)` as
  * empty element and `Changed` combined with any other `Change` results in a
  * `Changed` of the combined `T` values.
  */
sealed trait Change[T] extends Product with Serializable {
  def value: T

  final def someIfChanged: Option[T] =
    this match {
      case Changed(t) => Some(t)
      case _          => None
    }
}

object Change {
  final case class Changed[T](value: T) extends Change[T]
  final case class Unchanged[T](value: T) extends Change[T]

  implicit def changeEq[T](implicit T: Eq[T]): Eq[Change[T]] =
    Eq.instance { (c1, c2) =>
      (c1, c2) match {
        case (Changed(t1), Changed(t2))     => T.eqv(t1, t2)
        case (Unchanged(t1), Unchanged(t2)) => T.eqv(t1, t2)
        case _                              => false
      }
    }

  implicit def changeMonoid[T](implicit T: Monoid[T]): Monoid[Change[T]] =
    new Monoid[Change[T]] {
      override def empty: Change[T] = Unchanged(T.empty)

      override def combine(c1: Change[T], c2: Change[T]): Change[T] =
        (c1, c2) match {
          case (Unchanged(t1), Unchanged(t2)) => Unchanged(T.combine(t1, t2))
          case _                              => Changed(T.combine(c1.value, c2.value))
        }
    }
}
