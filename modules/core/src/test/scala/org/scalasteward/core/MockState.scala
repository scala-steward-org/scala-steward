package org.scalasteward.core

import better.files.File
import cats.data.StateT
import cats.effect.IO

final case class MockState(
    commands: Vector[List[String]],
    logs: Vector[(Option[Throwable], String)],
    files: Map[File, String]
) {
  def add(file: File, content: String): MockState =
    copy(files = files + (file -> content))

  def rm(file: File): MockState =
    copy(files = files - file)

  def exec(cmd: List[String]): MockState =
    copy(commands = commands :+ cmd)

  def log(maybeThrowable: Option[Throwable], msg: String): MockState =
    copy(logs = logs :+ ((maybeThrowable, msg)))
}

object MockState {
  type MockEff[A] = StateT[IO, MockState, A]

  def empty: MockState =
    MockState(Vector.empty, Vector.empty, Map.empty)
}
