/*
 * Copyright 2018-2025 Scala Steward contributors
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
