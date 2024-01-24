package org.scalasteward.core.mock

import better.files.File
import cats.effect.{IO, Ref}
import cats.syntax.all._
import org.http4s.{HttpApp, Uri}
import org.scalasteward.core.git.FileGitAlg
import org.scalasteward.core.git.FileGitAlgTest.ioAuxGitAlg
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockState.TraceEntry
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}

final case class MockState(
    trace: Vector[TraceEntry],
    commandOutputs: Map[Cmd, Either[Throwable, List[String]]],
    execCommands: Boolean,
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

  def initGitRepo(repoDir: File, files: (File, String)*): IO[MockState] =
    for {
      _ <- ioAuxGitAlg.createRepo(repoDir)
      state <- addFiles(files: _*)
      _ <- ioAuxGitAlg.addFiles(repoDir, files.map { case (file, _) => file }: _*)
    } yield state

  def rmFile(file: File): IO[MockState] =
    ioFileAlg.deleteForce(file).as(copy(files = files - file))

  def exec(cmd: String*): MockState =
    appendTraceEntry(Cmd(cmd: _*))

  def log(maybeThrowable: Option[Throwable], msg: String): MockState =
    appendTraceEntry(Log((maybeThrowable, msg)))

  def appendTraceEntry(entry: TraceEntry): MockState =
    copy(trace = trace :+ entry)

  def toRef: IO[Ref[IO, MockState]] =
    Ref[IO].of(this)
}

object MockState {
  def empty: MockState =
    MockState(
      trace = Vector.empty,
      commandOutputs = Map.empty,
      execCommands = false,
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

      def exec(workingDir: File, args: String*): Cmd =
        Cmd(List("VAR1=val1", "VAR2=val2", workingDir.toString) ++ args)

      def execSandboxed(workingDir: File, args: String*): Cmd =
        Cmd(
          List(
            workingDir.toString,
            "firejail",
            "--quiet",
            s"--whitelist=$workingDir",
            "--env=VAR1=val1",
            "--env=VAR2=val2"
          ) ++ args
        )

      def git(repoDir: File, args: String*): Cmd = {
        val env =
          List(s"GIT_ASKPASS=$mockRoot/askpass.sh", "VAR1=val1", "VAR2=val2", repoDir.toString)
        Cmd(env ++ FileGitAlg.gitCmd.toList ++ args)
      }

      def gitCommit(repoDir: File, messages: String*): Cmd = {
        val args =
          "commit" :: "--all" :: "--no-gpg-sign" :: messages.toList.flatMap(m => List("-m", m))
        git(repoDir, args: _*)
      }

      def gitGrep(repoDir: File, string: String): Cmd =
        git(repoDir, "grep", "-I", "--fixed-strings", "--files-with-matches", string)

      def gitLatestSha1(repoDir: File): Cmd =
        git(repoDir, "rev-parse", "--verify", "HEAD")

      def gitStatus(repoDir: File): Cmd =
        git(repoDir, "status", "--porcelain", "--untracked-files=no", "--ignore-submodules")
    }

    final case class Log(log: (Option[Throwable], String)) extends TraceEntry
    object Log {
      def apply(msg: String): Log = Log((None, msg))
    }
  }
}
