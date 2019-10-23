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

package org.scalasteward.core

import org.scalasteward.core.data.{Dependency, GroupId}
import org.scalasteward.core.sbt.defaultScalaBinaryVersion

package object ammonite {
  def parseAmmoniteScript(s: String): List[Dependency] =
    """[import\s*|\s*,\s*](?:\$plugin\.)?\$ivy\.\`([^`]*)\`""".r
      .findAllMatchIn(s)
      .map(_.group(1))
      .toList
      .flatMap(asDependency)

  private def asDependency(s: String): Option[Dependency] =
    s.split(":+").toList match {
      case group :: artifact :: version :: Nil =>
        Some(
          Dependency(
            GroupId(group),
            artifact,
            s"${artifact}_$defaultScalaBinaryVersion",
            version
          )
        )
      case _ => None
    }

}
