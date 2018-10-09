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

import better.files.File
import cats.effect.IO
import eu.timepit.scalasteward.model.Update
import fs2.Stream

object ioLegacy {
  def isSourceFile(file: File): Boolean =
    !file.pathAsString.contains(".git/") &&
      file.extension.exists(Set(".scala", ".sbt"))

  def updateDir(dir: File, update: Update): IO[Unit] =
    walk(dir).filter(isSourceFile).evalMap(updateFile(_, update)).compile.drain

  def updateFile(file: File, update: Update): IO[File] =
    IO(update.replaceAllIn(file.contentAsString).fold(file)(file.write(_)))

  def walk(dir: File): Stream[IO, File] =
    Stream.eval(IO(dir.walk())).flatMap(Stream.fromIterator[IO, File])
}
