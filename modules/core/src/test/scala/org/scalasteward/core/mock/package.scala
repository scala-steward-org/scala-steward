package org.scalasteward.core

import cats.data.Kleisli
import cats.effect.{Async, IO, Ref}
import cats.syntax.all.*
import cats.{~>, FlatMap}

package object mock {
  type MockCtx = Ref[IO, MockState]
  type MockEff[A] = Kleisli[IO, MockCtx, A]
  val MockEff: Async[MockEff] = Async[MockEff]

  val ioToMockEff: ~>[IO, MockEff] = new ~>[IO, MockEff] {
    override def apply[A](fa: IO[A]): MockEff[A] = Kleisli(_ => fa)
  }

  implicit class MockEffOps[A](private val fa: MockEff[A]) extends AnyVal {
    def runA(state: MockState): IO[A] =
      state.toRef.flatMap(fa.run)

    def runS(state: MockState): IO[MockState] =
      state.toRef.flatMap(ref => fa.run(ref) >> ref.get)

    def runSA(state: MockState): IO[(MockState, A)] =
      state.toRef.flatMap(ref => fa.run(ref).flatMap(a => ref.get.map(s => (s, a))))

    def unsafeRunSync(): A =
      runA(MockState.empty).unsafeRunSync()(cats.effect.unsafe.implicits.global)
  }

  def getFlatMapSet[F[_], A](f: A => F[A])(ref: Ref[F, A])(implicit F: FlatMap[F]): F[Unit] =
    ref.get.flatMap(f).flatMap(ref.set)
}
