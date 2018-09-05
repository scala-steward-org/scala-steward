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

import cats.effect.IO
import java.nio.file.Path // TODO: use File or Repository

object sbt {
  def dependencyUpdates(repoDir: Path): IO[List[DependencyUpdate]] =
    io.exec(List("sbt", "-no-colors", "dependencyUpdates"), repoDir)
      .map(toDependencyUpdates)

  def pluginsUpdates(repoDir: Path): IO[List[DependencyUpdate]] =
    io.exec(List("sbt", "-no-colors", ";reload plugins; dependencyUpdates"), repoDir)
      .map(toDependencyUpdates)

  def toDependencyUpdates(lines: List[String]): List[DependencyUpdate] =
    lines.flatMap { line =>
      DependencyUpdate.fromString(line.replace("[info]", "").trim).toSeq
    }
}
