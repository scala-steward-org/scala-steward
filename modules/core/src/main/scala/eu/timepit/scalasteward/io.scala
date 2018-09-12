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

package eu.timepit.scalasteward

import java.io.IOException
import java.util.concurrent.TimeUnit

import better.files.File
import cats.effect.IO
import cats.implicits._
import fs2.Stream

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.sys.process.{Process, ProcessLogger}

object io {
  val currentTimeMillis: IO[Long] =
    IO(System.currentTimeMillis())

  def deleteForce(dir: File): IO[Unit] =
    IO(if (dir.exists) dir.delete() else ())

  def exec(command: List[String], cwd: File): IO[List[String]] =
    IO {
      val lb = ListBuffer.empty[String]
      val log = new ProcessLogger {
        override def out(s: => String): Unit = lb.append(s)
        override def err(s: => String): Unit = lb.append(s)
        override def buffer[T](f: => T): T = f
      }
      val exitCode = Process(command, cwd.toJava).!(log)
      if (exitCode != 0) throw new IOException(lb.mkString("\n"))
      lb.result()
    }

  def isSourceFile(file: File): Boolean =
    !file.pathAsString.contains(".git/") &&
      file.extension.exists(Set(".scala", ".sbt"))

  def mkdirs(dir: File): IO[Unit] =
    IO(dir.createDirectories()).void

  def timed[A](fa: IO[A]): IO[(A, FiniteDuration)] =
    for {
      start <- currentTimeMillis
      a <- fa
      end <- currentTimeMillis
      duration = FiniteDuration(end - start, TimeUnit.MILLISECONDS)
    } yield (a, duration)

  def updateDir(dir: File, update: DependencyUpdate): IO[Unit] =
    walk(dir).filter(isSourceFile).evalMap(updateFile(_, update)).compile.drain

  def updateFile(file: File, update: DependencyUpdate): IO[File] =
    IO(update.replaceAllIn(file.contentAsString).fold(file)(file.write(_)))

  def walk(dir: File): Stream[IO, File] =
    Stream.eval(IO(dir.walk())).flatMap(Stream.fromIterator[IO, File])
}
