/*
 * Copyright 2018-2022 Scala Steward contributors
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
import cats.syntax.all._
import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.buildtool.sbt.{sbtArtifactId, sbtGroupId}
import org.scalasteward.core.data.Update
import org.scalasteward.core.scalafmt.{scalafmtArtifactId, scalafmtConfName, scalafmtGroupId}

package object io {
  def isSourceFile(update: Update, fileExtensions: Set[String])(file: File): Boolean = {
    val notInGitDir = !file.pathAsString.contains(".git/")
    notInGitDir && isSpecificOrGenericSourceFile(update, fileExtensions)(file)
  }

  private def isSpecificOrGenericSourceFile(update: Update, fileExtensions: Set[String])(
      file: File
  ): Boolean =
    () match {
      case _ if isSbtUpdate(update)              => file.name === "build.properties"
      case _ if isScalafmtCoreUpdate(update)     => file.name === scalafmtConfName
      case _ if MillAlg.isMillMainUpdate(update) => file.name === ".mill-version"
      case _                                     => isGenericSourceFile(file, fileExtensions)
    }

  private def isGenericSourceFile(file: File, fileExtensions: Set[String]): Boolean =
    fileExtensions.exists(file.name.endsWith)

  private def isSbtUpdate(update: Update): Boolean =
    update.groupId === sbtGroupId &&
      update.artifactIds.exists(_.name === sbtArtifactId.name)

  private def isScalafmtCoreUpdate(update: Update): Boolean =
    update.groupId === scalafmtGroupId &&
      update.artifactIds.exists(_.name === scalafmtArtifactId.name)
}
