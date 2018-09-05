/*
 * Copyright 2018 scruffy contributors
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

package eu.timepit.scruffy

import better.files.File
import cats.effect.IO
import cats.implicits._
import fs2.Stream
import java.io.IOException
import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

object io {
  def delete(root: File): IO[Unit] =
    IO(root.delete())

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

  def updateDependency(repo: Repository, update: DependencyUpdate): IO[Unit] =
    walk(repo.root).filter(isSourceFile).evalMap(updateDependency(_, update)).compile.drain

  def updateDependency(file: File, update: DependencyUpdate): IO[Unit] =
    IO {
      val regex = s"${update.artifactId}.*?${update.currentVersion}".r
      var updated = false
      val oldContent = file.contentAsString
      val newContent = regex.replaceAllIn(oldContent, m => {
        updated = true
        m.matched.replace(update.currentVersion, update.nextVersion)
      })
      if (updated) file.write(newContent)
      ()
    }

  def walk(root: File): Stream[IO, File] =
    Stream.eval(IO(root.walk())).flatMap(Stream.fromIterator[IO, File])
}
