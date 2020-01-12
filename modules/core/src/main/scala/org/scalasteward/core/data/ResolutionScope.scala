package org.scalasteward.core.data

import cats.Functor
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec

final case class ResolutionScope[A](value: A, resolvers: List[Resolver])

object ResolutionScope {
  type Dependency = ResolutionScope[Dependency]
  type Dependencies = ResolutionScope[List[Dependency]]

  implicit def resolutionScopeFunctor: Functor[ResolutionScope] =
    new Functor[ResolutionScope] {
      override def map[A, B](fa: ResolutionScope[A])(f: A => B): ResolutionScope[B] =
        fa.copy(value = f(fa.value))
    }

  implicit def resolutionScopeCodec[A: Decoder: Encoder]: Codec[ResolutionScope[A]] =
    deriveCodec
}
