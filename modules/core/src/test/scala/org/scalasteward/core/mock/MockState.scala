package org.scalasteward.core.mock

import better.files.File

final case class MockState(
    commands: Vector[List[String]],
    extraEnv: Vector[List[(String, String)]],
    logs: Vector[(Option[Throwable], String)],
    files: Map[File, String]
) {
  def add(file: File, content: String): MockState =
    copy(files = files + (file -> content))

  def addFiles(newFiles: Map[File, String]): MockState =
    copy(files = files ++ newFiles)

  def rm(file: File): MockState =
    copy(files = files - file)

  def exec(cmd: List[String], env: (String, String)*): MockState =
    // Vector() != Vector(List())
    if (env.isEmpty) copy(commands = commands :+ cmd)
    else copy(commands = commands :+ cmd, extraEnv :+ env.toList)

  def log(maybeThrowable: Option[Throwable], msg: String): MockState =
    copy(logs = logs :+ ((maybeThrowable, msg)))
}

object MockState {
  def empty: MockState =
    MockState(Vector.empty, Vector.empty, Vector.empty, Map.empty)
}
