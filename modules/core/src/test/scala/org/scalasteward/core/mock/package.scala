package org.scalasteward.core

import cats.Monad
import cats.data.Kleisli
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._

package object mock {
  type MockEff[A] = Kleisli[IO, Ref[IO, MockState], A]

  implicit class MockEffOps[A](private val fa: MockEff[A]) extends AnyVal {
    def runA(state: MockState): IO[A] =
      state.toRef.flatMap(fa.run)

    def runS(state: MockState): IO[MockState] =
      state.toRef.flatMap(ref => fa.run(ref) >> ref.get)

    def runSA(state: MockState): IO[(MockState, A)] =
      state.toRef.flatMap(ref => fa.run(ref).flatMap(a => ref.get.map(s => (s, a))))
  }

  def getFlatMapSet[F[_], A, B](f: A => F[A])(ref: Ref[F, A])(implicit F: Monad[F]): F[Unit] =
    ref.get.flatMap(f).flatMap(ref.set)
}
