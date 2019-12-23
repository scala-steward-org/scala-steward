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

import better.files.File
import cats.implicits._
import org.scalasteward.core.data.{GroupId, Update}

package object io {
  def isSourceFile(update: Update)(file: File): Boolean = {
    val notInGitDir = !file.pathAsString.contains(".git/")
    notInGitDir && isSpecificOrGenericSourceFile(update)(file)
  }

  private def isSpecificOrGenericSourceFile(update: Update)(file: File): Boolean =
    update match {
      case s: Update.Single if isSbtUpdate(s)          => file.name === "build.properties"
      case s: Update.Single if isScalafmtCoreUpdate(s) => file.name === ".scalafmt.conf"
      case _                                           => isGenericSourceFile(file)
    }

  private def isGenericSourceFile(file: File): Boolean = {
    val name = file.name
    val allowedByExtension = file.extension.exists(Set(".scala", ".sbt", ".sc"))
    val allowedByName = Set(".travis.yml").contains(name)
    val allowedBySuffix =
      Set(".sbt.shared").exists(suffix => name.endsWith(suffix) && !name.startsWith(suffix))
    allowedByExtension || allowedByName || allowedBySuffix
  }

  private def isSbtUpdate(update: Update.Single): Boolean =
    update.groupId === GroupId("org.scala-sbt") && update.artifactId.name === "sbt"

  private def isScalafmtCoreUpdate(update: Update.Single): Boolean =
    update.groupId === GroupId("org.scalameta") && update.artifactId.name === "scalafmt-core"
}
