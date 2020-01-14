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

package org.scalasteward.core.data

import cats.implicits._
import cats.{Applicative, Eval, Traverse}
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}

final case class ResolutionCtx[A](value: A, resolvers: List[Resolver])

object ResolutionCtx {
  type Dep = ResolutionCtx[Dependency]
  type Deps = ResolutionCtx[List[Dependency]]

  implicit def resolutionCtxTraverse: Traverse[ResolutionCtx] =
    new Traverse[ResolutionCtx] {
      override def traverse[G[_]: Applicative, A, B](
          fa: ResolutionCtx[A]
      )(f: A => G[B]): G[ResolutionCtx[B]] =
        f(fa.value).map(b => ResolutionCtx(b, fa.resolvers))

      override def foldLeft[A, B](fa: ResolutionCtx[A], b: B)(f: (B, A) => B): B =
        f(b, fa.value)

      override def foldRight[A, B](fa: ResolutionCtx[A], lb: Eval[B])(
          f: (A, Eval[B]) => Eval[B]
      ): Eval[B] =
        f(fa.value, lb)
    }

  implicit def resolutionCtxCodec[A: Decoder: Encoder]: Codec[ResolutionCtx[A]] =
    deriveCodec
}
