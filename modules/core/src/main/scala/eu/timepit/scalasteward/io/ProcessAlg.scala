/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.io

import better.files.File
import cats.data.{NonEmptyList => Nel}
import cats.effect.Sync
import cats.implicits._
import java.io.IOException
import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

trait ProcessAlg[F[_]] {
  def exec(command: Nel[String], cwd: File): F[List[String]]

  def execSandboxed(command: Nel[String], cwd: File): F[List[String]]
}

object ProcessAlg {
  def create[F[_]](implicit F: Sync[F]): ProcessAlg[F] =
    new ProcessAlg[F] {
      override def exec(command: Nel[String], cwd: File): F[List[String]] =
        F.delay {
          val lb = ListBuffer.empty[String]
          val log = new ProcessLogger {
            override def out(s: => String): Unit = lb.append(s)
            override def err(s: => String): Unit = lb.append(s)
            override def buffer[T](f: => T): T = f
          }
          val exitCode = Process(command.toList, cwd.toJava).!(log)
          if (exitCode != 0) throw new IOException(lb.mkString("\n"))
          lb.result()
        }

      override def execSandboxed(command: Nel[String], cwd: File): F[List[String]] =
        F.delay(File.home.pathAsString).flatMap { home =>
          val whitelisted = List(
            s"$home/.coursier",
            s"$home/.sbt",
            s"$home/.ivy2",
            cwd.pathAsString
          ).map(dir => s"--whitelist=$dir")
          exec(Nel("firejail", whitelisted) ::: command, cwd)
        }
    }
}
