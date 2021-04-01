package org.scalasteward.core

import cats.Parallel
import cats.effect.Sync
import cats.syntax.all._

import java.util.concurrent.ExecutorService

package object coursier {
  implicit def coursierSyncFromCatsEffectSync[F[_]](implicit
      parallel: Parallel[F],
      F: Sync[F]
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
        F.blocking(f)
    }
}
