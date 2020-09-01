/*
 * Copyright 2018-2020 Scala Steward contributors
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

import cats.effect._
import cats.implicits._
import fs2.Stream
import java.io.{File, IOException, InputStream}
import org.scalasteward.core.util._
import scala.collection.mutable.ListBuffer
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

object process {
  def slurp[F[_]](
      cmd: Nel[String],
      cwd: Option[File],
      extraEnv: Map[String, String],
      timeout: FiniteDuration,
      log: String => F[Unit],
      blocker: Blocker
  )(implicit contextShift: ContextShift[F], timer: Timer[F], F: Concurrent[F]): F[List[String]] =
    createProcess(cmd, cwd, extraEnv).flatMap { process =>
      F.delay(new ListBuffer[String]).flatMap { buffer =>
        val readOut = {
          val out = readInputStream[F](process.getInputStream, blocker)
          out.evalMap(line => F.delay(appendBounded(buffer, line, 4096)) >> log(line)).compile.drain
        }

        val showCmd = (extraEnv.map { case (k, v) => s"$k=$v" }.toList ++ cmd.toList).mkString_(" ")
        val result = readOut >> F.delay(process.waitFor()) >>= { exitValue =>
          if (exitValue === 0) F.pure(buffer.toList)
          else {
            val msg = s"'$showCmd' exited with code $exitValue"
            F.raiseError[List[String]](new IOException(makeMessage(msg, buffer.toList)))
          }
        }

        val fallback = F.delay(process.destroyForcibly()) >> {
          val msg = s"'$showCmd' timed out after ${timeout.toString}"
          F.raiseError[List[String]](new TimeoutException(makeMessage(msg, buffer.toList)))
        }

        Concurrent.timeoutTo(result, timeout, fallback)
      }
    }

  private def createProcess[F[_]](
      cmd: Nel[String],
      cwd: Option[File],
      extraEnv: Map[String, String]
  )(implicit F: Sync[F]): F[Process] =
    F.delay {
      val pb = new ProcessBuilder(cmd.toList: _*)
      val env = pb.environment()
      cwd.foreach(pb.directory)
      extraEnv.foreach { case (key, value) => env.put(key, value) }
      pb.redirectErrorStream(true)
      pb.start()
    }

  private def readInputStream[F[_]](is: InputStream, blocker: Blocker)(implicit
      F: Sync[F],
      cs: ContextShift[F]
  ): Stream[F, String] =
    fs2.io
      .readInputStream(F.pure(is), chunkSize = 4096, blocker)
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines)

  private def makeMessage(prefix: String, output: List[String]): String =
    (prefix :: output).mkString("\n")
}
