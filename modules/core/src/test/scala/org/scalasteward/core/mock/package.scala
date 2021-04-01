package org.scalasteward.core

import cats.Applicative
import cats.data.StateT
import cats.effect.{IO, Sync}
import cats.effect.kernel.{Async, Cont, Deferred, Fiber, Poll, Ref}
import cats.syntax.all._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

package object mock {
  type MockEff[A] = StateT[IO, MockState, A]

  def applyPure[F[_]: Applicative, S, A](f: S => (S, A)): StateT[F, S, A] =
    StateT.apply(s => f(s).pure[F])

  def fakeAsynFromSync[F[_]](implicit F: Sync[F]): Async[F] =
    new Async[F] {
      override def evalOn[A](fa: F[A], ec: ExecutionContext): F[A] = fa

      override def executionContext: F[ExecutionContext] = ???

      override def cont[K, R](body: Cont[F, K, R]): F[R] = ???

      override def suspend[A](hint: Sync.Type)(thunk: => A): F[A] = ???

      override def sleep(time: FiniteDuration): F[Unit] = ???

      override def ref[A](a: A): F[Ref[F, A]] = ???

      override def deferred[A]: F[Deferred[F, A]] = ???

      override def start[A](fa: F[A]): F[Fiber[F, Throwable, A]] = ???

      override def cede: F[Unit] = ???

      override def forceR[A, B](fa: F[A])(fb: F[B]): F[B] = ???

      override def uncancelable[A](body: Poll[F] => F[A]): F[A] = ???

      override def canceled: F[Unit] = ???

      override def onCancel[A](fa: F[A], fin: F[Unit]): F[A] = ???

      override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = ???

      override def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = ???

      override def raiseError[A](e: Throwable): F[A] = ???

      override def handleErrorWith[A](fa: F[A])(f: Throwable => F[A]): F[A] = ???

      override def pure[A](x: A): F[A] = ???

      override def monotonic: F[FiniteDuration] = ???

      override def realTime: F[FiniteDuration] = ???
    }

}
