/*
 * Copyright 2018-2019 Scala Steward contributors
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
import java.util.concurrent.Executors
import org.scalasteward.core.util.Nel
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, TimeoutException}

object process {
  def slurp[F[_]](
      cmd: Nel[String],
      cwd: Option[File],
      extraEnv: Map[String, String],
      timeout: FiniteDuration,
      log: String => F[Unit]
  )(implicit contextShift: ContextShift[F], timer: Timer[F], F: Concurrent[F]): F[List[String]] =
    F.delay {
        val pb = new ProcessBuilder(cmd.toList: _*)
        val env = pb.environment()
        cwd.foreach(pb.directory)
        extraEnv.foreach { case (key, value) => env.put(key, value) }
        pb.redirectErrorStream(true)
        pb.start()
      }
      .flatMap { process =>
        F.delay(new ListBuffer[String]).flatMap { buffer =>
          val readOut = blockingContext[F].use { ec =>
            val out = readInputStream[F](process.getInputStream, ec)
            out.evalMap(line => F.delay(buffer.append(line)) >> log(line)).compile.drain
          }

          val result = readOut.flatMap(_ => F.delay(process.waitFor())).flatMap { exitValue =>
            if (exitValue === 0) F.pure(buffer.toList)
            else F.raiseError[List[String]](new IOException(buffer.mkString("\n")))
          }

          val fallback = F.delay(process.destroyForcibly()) >>
            F.raiseError[List[String]](new TimeoutException(buffer.mkString("\n")))

          Concurrent.timeoutTo(result, timeout, fallback)
        }
      }

  private def blockingContext[F[_]](
      implicit F: Sync[F]
  ): Resource[F, ExecutionContextExecutorService] = {
    val alloc = F.delay(ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()))
    Resource.make(alloc)(ec => F.delay(ec.shutdown()))
  }

  private def readInputStream[F[_]](is: InputStream, blockingContext: ExecutionContext)(
      implicit
      F: Sync[F],
      cs: ContextShift[F]
  ): Stream[F, String] =
    fs2.io
      .readInputStream(F.pure(is), chunkSize = 4096, blockingContext)
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines)
}
