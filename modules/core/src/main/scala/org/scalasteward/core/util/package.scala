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

package org.scalasteward.core

import cats._
import cats.effect.Bracket
import cats.implicits._
import eu.timepit.refined.types.numeric.PosInt
import fs2.Pipe
import scala.annotation.tailrec
import scala.collection.TraversableLike
import scala.collection.mutable.ListBuffer

package object util {
  final type Nel[+A] = cats.data.NonEmptyList[A]
  final val Nel = cats.data.NonEmptyList

  type ApplicativeThrowable[F[_]] = ApplicativeError[F, Throwable]

  type MonadThrowable[F[_]] = MonadError[F, Throwable]

  type BracketThrowable[F[_]] = Bracket[F, Throwable]

  /** Binds the elements of `gfb` until the first `F[Boolean]` that
    * evaluates to `true`.
    *
    * @example {{{
    * scala> import cats.data.State
    *
    * scala> bindUntilTrue(Nel.of(
    *      |   State((l: List[Int]) => (l :+ 1, false)),
    *      |   State((l: List[Int]) => (l :+ 2, true )),
    *      |   State((l: List[Int]) => (l :+ 3, false)),
    *      |   State((l: List[Int]) => (l :+ 4, true ))
    *      | )).runS(List(0)).value
    * res1: List[Int] = List(0, 1, 2)
    * }}}
    */
  def bindUntilTrue[G[_]: Foldable, F[_]: Monad](gfb: G[F[Boolean]]): F[Boolean] =
    gfb.existsM(identity)

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

  def evalFilter[F[_]: Functor, A](p: A => F[Boolean]): Pipe[F, A, A] =
    _.evalMap(a => p(a).tupleLeft(a)).collect { case (a, true) => a }

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

  /** Removes all elements from `nel` which also exist in `as`. */
  def removeAll[F[_]: UnorderedFoldable, A: Eq](nel: Nel[A], as: F[A]): Option[Nel[A]] =
    Nel.fromList(nel.toList.filterNot(a => as.exists(_ === a)))

  /** Splits a list into chunks with maximum size `maxSize` such that
    * each chunk only consists of distinct elements with regards to the
    * discriminator function `f`.
    *
    * @example {{{
    * scala> import cats.implicits._
    *      | import eu.timepit.refined.types.numeric.PosInt
    *
    * scala> separateBy(List("a", "b", "cd", "efg", "hi", "jk", "lmn"))(PosInt(3))(_.length)
    * res1: List[Nel[String]] = List(NonEmptyList(a, cd, efg), NonEmptyList(b, hi, lmn), NonEmptyList(jk))
    * }}}
    */
  def separateBy[A, K: Eq](list: List[A])(maxSize: PosInt)(f: A => K): List[Nel[A]] = {
    @tailrec
    def innerLoop(
        unseen: List[A],
        queue: ListBuffer[(A, K)],
        fromQueue: Boolean,
        chunkA: ListBuffer[A],
        chunkK: List[K]
    ): (List[A], ListBuffer[(A, K)], ListBuffer[A]) =
      unseen match {
        case _ if chunkA.size >= maxSize.value => (unseen, queue, chunkA)

        case _ if fromQueue =>
          queue.find { case (_, k) => !chunkK.contains_(k) } match {
            case Some(ak @ (a, k)) =>
              val queue1 = queue -= ak
              innerLoop(unseen, queue1, queue1.nonEmpty, chunkA :+ a, k :: chunkK)
            case None =>
              innerLoop(unseen, queue, false, chunkA, chunkK)
          }

        case a :: as =>
          val k = f(a)
          if (chunkK.contains_(k))
            innerLoop(as, queue :+ ((a, k)), false, chunkA, chunkK)
          else
            innerLoop(as, queue, false, chunkA :+ a, k :: chunkK)

        case Nil => (unseen, queue, chunkA)
      }

    @tailrec
    def outerLoop(
        unseen: List[A],
        queue: ListBuffer[(A, K)],
        acc: ListBuffer[Nel[A]]
    ): ListBuffer[Nel[A]] = {
      val (unseen1, queue1, chunk) = innerLoop(unseen, queue, queue.nonEmpty, ListBuffer.empty, Nil)
      val acc1 = Nel.fromList(chunk.toList).fold(acc)(acc :+ _)
      if (unseen1.nonEmpty || queue1.nonEmpty) outerLoop(unseen1, queue1, acc1) else acc1
    }

    outerLoop(list, ListBuffer.empty, ListBuffer.empty).toList
  }
}
