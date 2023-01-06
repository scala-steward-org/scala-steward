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
import org.scalasteward.core.util._
import scala.collection.mutable.ListBuffer
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

object process {
  final case class Args(
      command: Nel[String],
      workingDirectory: Option[File] = None,
      extraEnv: List[(String, String)] = Nil,
      clearEnv: Boolean = false
  )

  def slurp[F[_]](
      args: Args,
      timeout: FiniteDuration,
      maxBufferSize: Int,
      log: String => F[Unit]
  )(implicit F: Async[F]): F[List[String]] =
    createProcess(args).flatMap { process =>
      F.delay(new ListBuffer[String]).flatMap { buffer =>
        val readOut = readInputStream[F](process.getInputStream)
          .evalMap(line => F.delay(appendBounded(buffer, line, maxBufferSize)) >> log(line))
          .compile
          .drain

        val result = readOut >> F.blocking(process.waitFor()) >>= { exitValue =>
          if (exitValue === 0) F.pure(buffer.toList)
          else {
            val msg = s"'${showCmd(args)}' exited with code $exitValue"
            F.raiseError[List[String]](new IOException(makeMessage(msg, buffer.toList)))
          }
        }

        val fallback = F.blocking(process.destroyForcibly()) >> {
          val msg = s"'${showCmd(args)}' timed out after ${timeout.toString}"
          F.raiseError[List[String]](new TimeoutException(makeMessage(msg, buffer.toList)))
        }

        F.timeoutAndForget(result, timeout).recoverWith { case _: TimeoutException => fallback }
      }
    }

  def showCmd(args: Args): String =
    (args.extraEnv.map { case (k, v) => s"$k=$v" } ++ args.command.toList).mkString_(" ")

  private def createProcess[F[_]](args: Args)(implicit F: Sync[F]): F[Process] =
    F.blocking {
      val pb = new ProcessBuilder(args.command.toList: _*)
      args.workingDirectory.foreach(file => pb.directory(file.toJava))
      val env = pb.environment()
      if (args.clearEnv) env.clear()
      args.extraEnv.foreach { case (key, value) => env.put(key, value) }
      pb.redirectErrorStream(true)
      pb
    }.flatMap { pb =>
      F.blocking {
        val p = pb.start()
        // Close standard input so that the process never waits for input.
        p.getOutputStream.close()
        p
      }
    }

  private def readInputStream[F[_]](is: InputStream)(implicit F: Sync[F]): Stream[F, String] =
    fs2.io
      .readInputStream(F.pure(is), chunkSize = 4096)
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)

  private def makeMessage(prefix: String, output: List[String]): String =
    (prefix :: output).mkString("\n")
}
