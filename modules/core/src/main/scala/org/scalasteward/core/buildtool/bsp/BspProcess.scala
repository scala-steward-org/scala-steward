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

package org.scalasteward.core.buildtool.bsp

import better.files.File
import cats.effect.implicits._
import cats.effect.{Async, Resource}
import cats.syntax.all._
import ch.epfl.scala.bsp4j._
import java.util
import java.util.Collections
import java.util.concurrent.{AbstractExecutorService, ExecutorService, TimeUnit}
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.scalasteward.core.util.Nel
import scala.concurrent.ExecutionContext

object BspProcess {
  def run[F[_]](
      command: Nel[String],
      workingDirectory: File
  )(implicit F: Async[F]): Resource[F, BuildServer] =
    for {
      process <- Resource.make(F.blocking {
        val pb = new ProcessBuilder(command.toList: _*)
        pb.directory(workingDirectory.toJava)
        pb.start()
      })(process => F.blocking(process.waitFor(1L, TimeUnit.SECONDS)).void)
      ec <- Resource.executionContext
      launcher <- Resource.eval(F.blocking {
        new Launcher.Builder[BuildServer]()
          .setOutput(process.getOutputStream)
          .setInput(process.getInputStream)
          .setExecutorService(fromExecutionContext(ec))
          .setLocalService(new BspClient)
          .setRemoteInterface(classOf[BuildServer])
          .create()
      })
      _ <- F.blocking(launcher.startListening()).background
    } yield launcher.getRemoteProxy

  private def fromExecutionContext(ec: ExecutionContext): ExecutorService =
    new AbstractExecutorService {
      override def shutdown(): Unit = ()
      override def shutdownNow(): util.List[Runnable] = Collections.emptyList()
      override def isShutdown: Boolean = false
      override def isTerminated: Boolean = false
      override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = false
      override def execute(command: Runnable): Unit = ec.execute(command)
    }
}
