package org.scalasteward.core.mock

import better.files.File
import cats.effect.{IO, Ref}
import cats.syntax.all._
import org.http4s.{HttpApp, Uri}
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.MockState.TraceEntry
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}

final case class MockState(
    trace: Vector[TraceEntry],
    commandOutputs: Map[List[String], Either[Throwable, List[String]]],
    files: Map[File, String],
    uris: Map[Uri, String],
    clientResponses: HttpApp[MockEff]
) {
  def addFiles(newFiles: (File, String)*): IO[MockState] =
    newFiles.toList
      .traverse_ { case (file, content) => ioFileAlg.writeFile(file, content) }
      .as(copy(files = files ++ newFiles))

  def addUris(newUris: (Uri, String)*): MockState =
    copy(uris = uris ++ newUris)

  def rmFile(file: File): IO[MockState] =
    ioFileAlg.deleteForce(file).as(copy(files = files - file))

  def exec(cmd: List[String], env: (String, String)*): MockState =
    copy(trace = trace :+ Cmd(env.map { case (k, v) => s"$k=$v" }.toList ++ cmd))

  def log(maybeThrowable: Option[Throwable], msg: String): MockState =
    copy(trace = trace :+ Log((maybeThrowable, msg)))

  def toRef: IO[Ref[IO, MockState]] =
    Ref[IO].of(this)
}

object MockState {
  def empty: MockState =
    MockState(
      trace = Vector.empty,
      commandOutputs = Map.empty,
      files = Map.empty,
      uris = Map.empty,
      clientResponses = HttpApp.notFound
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
