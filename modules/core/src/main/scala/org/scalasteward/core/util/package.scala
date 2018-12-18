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
import cats.{ApplicativeError, Eq, Foldable, MonadError, Semigroup, UnorderedFoldable}
import eu.timepit.refined.types.numeric.PosInt
import scala.annotation.tailrec
import scala.collection.TraversableLike
import scala.collection.mutable.ListBuffer

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

  def halve[C](c: C)(implicit ev: C => TraversableLike[_, C]): Either[C, (C, C)] = {
    val size = c.size
    if (size < 2) Left(c) else Right(c.splitAt((size + 1) / 2))
  }

  /** Returns true if there is an element that is both in `fa` and `ga`. */
  def intersects[F[_]: UnorderedFoldable, G[_]: UnorderedFoldable, A: Eq](
      fa: F[A],
      ga: G[A]
  ): Boolean =
    fa.exists(a => ga.exists(b => a === b))

  /** Splits a list into chunks with maximum size `maxSize` such that each chunk
    * only consists of distinct elements with regards to the discriminator
    * function `f`.
    */
  def separateBy[A, K: Eq](list: List[A])(maxSize: PosInt)(f: A => K): List[Nel[A]] = {
    def append(currA: ListBuffer[A], acc: ListBuffer[Nel[A]]): ListBuffer[Nel[A]] =
      Nel.fromList(currA.toList).fold(acc)(acc :+ _)

    @tailrec
    def loop(
        unseen: List[A],
        queue: ListBuffer[(A, K)],
        fromQueue: Boolean,
        currA: ListBuffer[A],
        currK: List[K],
        acc: ListBuffer[Nel[A]]
    ): ListBuffer[Nel[A]] =
      (unseen, queue, fromQueue) match {
        case _ if currA.size >= maxSize.value =>
          loop(unseen, queue, true, ListBuffer.empty, Nil, append(currA, acc))

        case (_, _ +: _, true) =>
          queue.find { case (_, k) => !currK.contains_(k) } match {
            case Some(ak @ (a, k)) =>
              loop(unseen, queue -= ak, true, currA :+ a, k :: currK, acc)
            case None =>
              loop(unseen, queue, false, currA, currK, acc)
          }

        case (a :: as, _, _) =>
          val k = f(a)
          if (currK.contains_(k))
            loop(as, queue :+ ((a, k)), false, currA, currK, acc)
          else
            loop(as, queue, false, currA :+ a, k :: currK, acc)

        case (Nil, (a, k) +: aks, false) =>
          loop(Nil, aks, true, ListBuffer(a), k :: Nil, append(currA, acc))

        case (Nil, ListBuffer(), _) => append(currA, acc)
      }

    loop(list, ListBuffer.empty, false, ListBuffer.empty, Nil, ListBuffer.empty).toList
  }
}
