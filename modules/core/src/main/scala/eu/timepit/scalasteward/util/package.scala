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

import cats.effect.Bracket
import cats.implicits._
import cats.{ApplicativeError, Foldable, MonadError, Semigroup}

package object util {
  final type Nel[+A] = cats.data.NonEmptyList[A]
  final val Nel = cats.data.NonEmptyList

  type ApplicativeThrowable[F[_]] = ApplicativeError[F, Throwable]

  type MonadThrowable[F[_]] = MonadError[F, Throwable]

  type BracketThrowable[F[_]] = Bracket[F, Throwable]

  def divideOnError[F[_], G[_], A, B, E](divide: A => G[A])(f: A => F[B])(a: A)(
      implicit
      F: ApplicativeError[F, E],
      G: Foldable[G],
      B: Semigroup[B]
  ): F[B] =
    f(a).handleErrorWith { e =>
      Nel.fromFoldable(divide(a)) match {
        case None      => F.raiseError(e)
        case Some(nel) => nel.traverse(divideOnError(divide)(f)(_)).map(_.reduce)
      }
    }

  def divideOnError2[F[_], G[_], A, B, E](a: A)(f: A => F[B])(divide: A => G[A])(
      handleError: E => F[B]
  )(
      implicit
      F: ApplicativeError[F, E],
      G: Foldable[G],
      B: Semigroup[B]
  ): F[B] = {
    def loop(a: A): F[B] =
      f(a).handleErrorWith { e =>
        Nel.fromFoldable(divide(a)) match {
          case None      => handleError(e)
          case Some(nel) => nel.traverse(loop).map(_.reduce)
        }
      }
    loop(a)
  }

  def halve[A](list: List[A]): List[List[A]] =
    if (list.isEmpty) Nil
    else {
      val (fst, snd) = list.splitAt((list.size + 1) / 2)
      List(fst, snd)
    }
}
