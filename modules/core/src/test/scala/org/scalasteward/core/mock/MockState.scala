package org.scalasteward.core.mock

import better.files.File
import org.http4s.Uri
import org.scalasteward.core.mock.MockState.TraceEntry
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}

final case class MockState(
    trace: Vector[TraceEntry],
    commandOutputs: Map[List[String], List[String]],
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
    copy(trace = trace :+ Cmd(env.map { case (k, v) => s"$k=$v" }.toList ++ cmd))

  def log(maybeThrowable: Option[Throwable], msg: String): MockState =
    copy(trace = trace :+ Log((maybeThrowable, msg)))
}

object MockState {
  def empty: MockState =
    MockState(
      trace = Vector.empty,
      commandOutputs = Map.empty,
      files = Map.empty,
      uris = Map.empty
    )

  sealed trait TraceEntry extends Product with Serializable
  object TraceEntry {
    final case class Cmd(cmd: List[String]) extends TraceEntry
    object Cmd {
      def apply(args: String*): Cmd = apply(args.toList)
      def apply(args1: List[String], args2: String*): Cmd = apply(args1 ++ args2.toList)
    }

    final case class Log(log: (Option[Throwable], String)) extends TraceEntry
    object Log {
      def apply(msg: String): Log = Log((None, msg))
    }
  }
}
