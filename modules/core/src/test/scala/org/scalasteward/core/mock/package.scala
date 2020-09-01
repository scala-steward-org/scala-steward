package org.scalasteward.core

import cats.Applicative
import cats.data.StateT
import cats.effect.IO
import cats.syntax.all._

package object mock {
  type MockEff[A] = StateT[IO, MockState, A]

  def applyPure[F[_]: Applicative, S, A](f: S => (S, A)): StateT[F, S, A] =
    StateT.apply(s => f(s).pure[F])
}
