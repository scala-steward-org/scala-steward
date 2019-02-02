package org.scalasteward.core

import cats.data.StateT
import cats.effect.IO

package object mock {
  type MockEff[A] = StateT[IO, MockState, A]
}
