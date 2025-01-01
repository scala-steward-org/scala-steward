/*
 * Copyright 2018-2023 Scala Steward contributors
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

import cats.implicits._
import cats.{Applicative, Eval, Order, Traverse}
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}

/** A container of a value of type `A` with associated resolvers.
  *
  * In most cases `Scope` contains dependencies extracted from a build in which these are always
  * defined in the context of resolvers.
  */
final case class Scope[A](value: A, resolvers: List[Resolver])

object Scope {
  type Dependency = Scope[org.scalasteward.core.data.Dependency]
  type Dependencies = Scope[List[org.scalasteward.core.data.Dependency]]

  def combineByResolvers[A: Order](scopes: List[Scope[List[A]]]): List[Scope[List[A]]] =
    scopes.groupByNel(_.resolvers).toList.map { case (resolvers, group) =>
      Scope(group.reduceMap(_.value).distinct.sorted, resolvers)
    }

  implicit def scopeTraverse: Traverse[Scope] =
    new Traverse[Scope] {
      override def traverse[G[_]: Applicative, A, B](fa: Scope[A])(f: A => G[B]): G[Scope[B]] =
        f(fa.value).map(b => fa.copy(value = b))

      override def foldLeft[A, B](fa: Scope[A], b: B)(f: (B, A) => B): B =
        f(b, fa.value)

      override def foldRight[A, B](fa: Scope[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
        f(fa.value, lb)
    }

  implicit def scopeCodec[A: Decoder: Encoder]: Codec[Scope[A]] =
    deriveCodec

  implicit def scopeOrder[A: Order]: Order[Scope[A]] =
    Order.by((scope: Scope[A]) => (scope.value, scope.resolvers))
}
