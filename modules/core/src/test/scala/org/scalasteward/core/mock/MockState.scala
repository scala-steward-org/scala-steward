package org.scalasteward.core.mock

import better.files.File
import org.http4s.Uri

final case class MockState(
    commands: Vector[List[String]],
    commandOutputs: Map[List[String], List[String]],
    logs: Vector[(Option[Throwable], String)],
    files: Map[File, String],
    uris: Map[Uri, String]
) {
  def add(file: File, content: String): MockState =
    copy(files = files + (file -> content))

  def addFiles(newFiles: Map[File, String]): MockState =
    copy(files = files ++ newFiles)

  def addUri(uri: Uri, content: String): MockState =
    copy(uris = uris + (uri -> content))

  def rm(file: File): MockState =
    copy(files = files - file)

  def exec(cmd: List[String], env: (String, String)*): MockState =
    copy(commands = commands :+ (env.map { case (k, v) => s"$k=$v" }.toList ++ cmd))

  def log(maybeThrowable: Option[Throwable], msg: String): MockState =
    copy(logs = logs :+ ((maybeThrowable, msg)))
}

object MockState {
  def empty: MockState =
    MockState(
      commands = Vector.empty,
      commandOutputs = Map.empty,
      logs = Vector.empty,
      files = Map.empty,
      uris = Map.empty
    )
}
