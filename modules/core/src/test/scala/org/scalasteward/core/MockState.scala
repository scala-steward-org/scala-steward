package org.scalasteward.core

import cats.data.State
import org.scalasteward.core.util.MonadThrowable
import cats.{Monad, MonadError}

final case class MockState(
    commands: Vector[List[String]],
    logs: Vector[(Option[Throwable], String)]
) {
  def exec(cmd: List[String]): MockState =
    copy(commands = commands :+ cmd)

  def log(maybeThrowable: Option[Throwable], msg: String): MockState =
    copy(logs = logs :+ ((maybeThrowable, msg)))
}

object MockState {
  type MockEnv[A] = State[MockState, A]

  def empty: MockState =
    MockState(Vector.empty, Vector.empty)

  // Unable to make this implicit. Ambiguous implicit error
  def monadErrorInstance(implicit M: Monad[MockEnv]): MonadThrowable[MockEnv] =
    new MonadError[MockEnv, Throwable] {
      def pure[A](x: A): MockEnv[A] = M.pure(x)
      def raiseError[A](e: Throwable): MockEnv[A] = throw e
      def tailRecM[A, B](a: A)(f: A => MockEnv[Either[A, B]]): MockEnv[B] = M.tailRecM(a)(f)
      def flatMap[A, B](fa: MockEnv[A])(f: A => MockEnv[B]): MockEnv[B] = M.flatMap(fa)(f)
      def handleErrorWith[A](fa: MockEnv[A])(f: Throwable => MockEnv[A]): MockEnv[A] =
        try {
          for {
            a <- fa
          } yield a
        } catch {
          case t: Throwable => f(t)
        }
    }
}
