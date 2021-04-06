package org.scalasteward.core

import cats.FlatMap
import cats.data.Kleisli
import cats.effect.{Async, IO, Ref}
import cats.syntax.all._

package object mock {
  type MockEff[A] = Kleisli[IO, Ref[IO, MockState], A]
  val MockEff: Async[MockEff] = Async[MockEff]

  implicit class MockEffOps[A](private val fa: MockEff[A]) extends AnyVal {
    def runA(state: MockState): IO[A] =
      state.toRef.flatMap(fa.run)

    def runS(state: MockState): IO[MockState] =
      state.toRef.flatMap(ref => fa.run(ref) >> ref.get)

    def runSA(state: MockState): IO[(MockState, A)] =
      state.toRef.flatMap(ref => fa.run(ref).flatMap(a => ref.get.map(s => (s, a))))
  }

  def getFlatMapSet[F[_], A, B](f: A => F[A])(ref: Ref[F, A])(implicit F: FlatMap[F]): F[Unit] =
    ref.get.flatMap(f).flatMap(ref.set)
}
