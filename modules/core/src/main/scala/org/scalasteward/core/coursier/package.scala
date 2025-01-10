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

import cats.Parallel
import cats.effect.Async
import cats.syntax.all.*
import java.util.concurrent.ExecutorService
import scala.concurrent.ExecutionContext

package object coursier {
  implicit def coursierSyncFromCatsEffectSync[F[_]](implicit
      parallel: Parallel[F],
      F: Async[F]
  ): _root_.coursier.util.Sync[F] =
    new _root_.coursier.util.Sync[F] {
      override def delay[A](a: => A): F[A] =
        F.delay(a)

      override def handle[A](a: F[A])(f: PartialFunction[Throwable, A]): F[A] =
        F.recover(a)(f)

      override def fromAttempt[A](a: Either[Throwable, A]): F[A] =
        F.fromEither(a)

      override def gather[A](elems: Seq[F[A]]): F[Seq[A]] =
        elems.parSequence

      override def point[A](a: A): F[A] =
        F.pure(a)

      override def bind[A, B](elem: F[A])(f: A => F[B]): F[B] =
        F.flatMap(elem)(f)

      override def schedule[A](pool: ExecutorService)(f: => A): F[A] =
        F.evalOn(F.delay(f), ExecutionContext.fromExecutorService(pool))
    }
}
