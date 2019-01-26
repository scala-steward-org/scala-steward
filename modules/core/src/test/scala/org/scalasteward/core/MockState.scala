package org.scalasteward.core

import cats.data.StateT
import cats.effect.IO

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
  type MockEnv[A] = StateT[IO, MockState, A]

  def empty: MockState =
    MockState(Vector.empty, Vector.empty)
}
