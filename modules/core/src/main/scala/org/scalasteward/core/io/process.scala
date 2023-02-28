/*
 * Copyright 2018-2023 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.io

import better.files.File
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import java.io.{IOException, InputStream}
import org.scalasteward.core.application.Cli
import org.scalasteward.core.util._
import scala.collection.mutable.ListBuffer
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

object process {
  final case class Args(
      command: Nel[String],
      workingDirectory: Option[File] = None,
      extraEnv: List[(String, String)] = Nil,
      slurpOptions: SlurpOptions = Set.empty
  )

  sealed trait SlurpOption extends Product with Serializable
  object SlurpOption {
    case object ClearEnvironment extends SlurpOption

    /** [[slurp]] uses an internal buffer with a fixed maximum size for the output of a process. If
      * a process outputs more lines than the maximum buffer size, [[slurp]] raises an
      * [[ProcessBufferOverflowException]]. If this option is given, the buffer overflow will be
      * ignored and no exception is raised. Note that in these cases only the last lines up until
      * the maximum buffer size are returned.
      */
    case object IgnoreBufferOverflow extends SlurpOption
  }

  type SlurpOptions = Set[SlurpOption]
  object SlurpOptions {
    val ignoreBufferOverflow: SlurpOptions = Set(SlurpOption.IgnoreBufferOverflow)
  }

  def slurp[F[_]](
      args: Args,
      timeout: FiniteDuration,
      maxBufferSize: Int,
      log: String => F[Unit]
  )(implicit F: Async[F]): F[List[String]] =
    createProcess(args).flatMap { process =>
      F.delay(new ListBuffer[String]).flatMap { buffer =>
        val raiseError = F.raiseError[List[String]] _

        val result =
          readLinesIntoBuffer(process.getInputStream, buffer, maxBufferSize, log).flatMap {
            maxSizeExceeded =>
              F.blocking(process.waitFor()).flatMap { exitValue =>
                if (maxSizeExceeded && !args.slurpOptions(SlurpOption.IgnoreBufferOverflow))
                  raiseError(new ProcessBufferOverflowException(args, buffer, maxBufferSize))
                else if (exitValue === 0)
                  F.pure(buffer.toList)
                else
                  raiseError(new ProcessFailedException(args, buffer, exitValue))
              }
          }

        val onTimeout = F.blocking(process.destroyForcibly()) >>
          raiseError(new ProcessTimedOutException(args, buffer, timeout))

        F.timeoutAndForget(result, timeout).recoverWith { case _: TimeoutException => onTimeout }
      }
    }

  def showCmd(args: Args): String =
    (args.extraEnv.map { case (k, v) => s"$k=$v" } ++ args.command.toList).mkString_(" ")

  private def createProcessBuilder[F[_]](args: Args)(implicit F: Sync[F]): F[ProcessBuilder] =
    F.blocking {
      val pb = new ProcessBuilder(args.command.toList: _*)
      args.workingDirectory.foreach(file => pb.directory(file.toJava))
      val env = pb.environment()
      if (args.slurpOptions(SlurpOption.ClearEnvironment)) env.clear()
      args.extraEnv.foreach { case (key, value) => env.put(key, value) }
      pb.redirectErrorStream(true)
      pb
    }

  private def createProcess[F[_]](args: Args)(implicit F: Sync[F]): F[Process] =
    createProcessBuilder(args).flatMap { pb =>
      F.blocking {
        val p = pb.start()
        // Close standard input so that the process never waits for input.
        p.getOutputStream.close()
        p
      }
    }

  /** Reads lines from `is` into `buffer` such that `buffer` contains no more than `maxBufferSize`
    * lines.
    *
    * @return
    *   `true` if more than `maxBufferSize` lines were added to the buffer, `false` otherwise
    */
  private def readLinesIntoBuffer[F[_]](
      is: InputStream,
      buffer: ListBuffer[String],
      maxBufferSize: Int,
      log: String => F[Unit]
  )(implicit F: Sync[F]): F[Boolean] =
    readUtf8Lines[F](is, maxBufferSize)
      .evalMap(line => log(line).as(appendBounded(buffer, line, maxBufferSize)))
      .compile
      .fold(false)(_ || _)

  private def readUtf8Lines[F[_]](
      is: InputStream,
      maxBufferSize: Int
  )(implicit F: Sync[F]): Stream[F, String] =
    fs2.io
      .readInputStream(F.pure(is), chunkSize = 8192)
      .through(fs2.text.utf8.decode)
      .through(fs2.text.linesLimited(maxBufferSize))

  final class ProcessBufferOverflowException(
      args: Args,
      buffer: ListBuffer[String],
      maxBufferSize: Int
  ) extends IOException(makeMessage(args, buffer) {
        s"outputted more than $maxBufferSize lines. " +
          s"If the process executed normally and the buffer size is just too small, you can " +
          s"increase it with the --${Cli.name.maxBufferSize} command-line option and/or open " +
          s"a pull request in ${org.scalasteward.core.BuildInfo.gitHubUrl} that increases the " +
          s"default buffer size."
      })

  final class ProcessFailedException(
      args: Args,
      buffer: ListBuffer[String],
      val exitValue: Int
  ) extends IOException(makeMessage(args, buffer)(s"exited with code $exitValue."))

  final class ProcessTimedOutException(
      args: Args,
      buffer: ListBuffer[String],
      timeout: FiniteDuration
  ) extends TimeoutException(makeMessage(args, buffer) {
        s"timed out after ${timeout.toString}. " +
          s"If the process executed normally but just takes a long time, you can increase the " +
          s"timeout with the --${Cli.name.processTimeout} command-line option."
      })

  private def makeMessage(args: Args, buffer: ListBuffer[String])(description: String): String =
    buffer.prepend(s"'${showCmd(args)}' $description").mkString("\n")
}
