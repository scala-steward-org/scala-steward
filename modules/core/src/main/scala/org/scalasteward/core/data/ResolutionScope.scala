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

import cats.Functor
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec

final case class ResolutionScope[A](value: A, resolvers: List[Resolver])

object ResolutionScope {
  type Dependency = ResolutionScope[org.scalasteward.core.data.Dependency]
  type Dependencies = ResolutionScope[List[org.scalasteward.core.data.Dependency]]

  implicit def resolutionScopeFunctor: Functor[ResolutionScope] =
    new Functor[ResolutionScope] {
      override def map[A, B](fa: ResolutionScope[A])(f: A => B): ResolutionScope[B] =
        fa.copy(value = f(fa.value))
    }

  implicit def resolutionScopeCodec[A: Decoder: Encoder]: Codec[ResolutionScope[A]] =
    deriveCodec
}
