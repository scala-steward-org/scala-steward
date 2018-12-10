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

package org.scalasteward.core

import cats.effect.Bracket
import cats.implicits._
import cats.{ApplicativeError, Foldable, MonadError, Semigroup}
import scala.collection.TraversableLike

package object util {
  final type Nel[+A] = cats.data.NonEmptyList[A]
  final val Nel = cats.data.NonEmptyList

  type ApplicativeThrowable[F[_]] = ApplicativeError[F, Throwable]

  type MonadThrowable[F[_]] = MonadError[F, Throwable]

  type BracketThrowable[F[_]] = Bracket[F, Throwable]

  def divideOnError[F[_], G[_], A, B, E](a: A)(f: A => F[B])(divide: A => G[A])(
      handleError: (A, E) => F[B]
  )(
      implicit
      F: ApplicativeError[F, E],
      G: Foldable[G],
      B: Semigroup[B]
  ): F[B] = {
    def loop(a1: A): F[B] =
      f(a1).handleErrorWith { e =>
        Nel.fromFoldable(divide(a1)) match {
          case None      => handleError(a1, e)
          case Some(nel) => nel.traverse(loop).map(_.reduce)
        }
      }
    loop(a)
  }

  def halve[A, C](c: C)(implicit ev: C => TraversableLike[A, C]): Either[C, (C, C)] = {
    val size = c.size
    if (size < 2) Left(c) else Right(c.splitAt((size + 1) / 2))
  }
}
