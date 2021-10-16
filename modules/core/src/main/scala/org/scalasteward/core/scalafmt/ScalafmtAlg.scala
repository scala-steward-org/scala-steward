/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core.scalafmt

import cats.Monad
import cats.data.OptionT
import cats.syntax.all._
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.{Scope, Version}
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafmt.ScalafmtAlg.parseScalafmtConf
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.BuildRoot

final class ScalafmtAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  def getScalafmtVersion(buildRoot: BuildRoot): F[Option[Version]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      scalafmtConfFile = buildRootDir / scalafmtConfName
      fileContent <- fileAlg.readFile(scalafmtConfFile)
    } yield fileContent.flatMap(parseScalafmtConf)

  def getScopedScalafmtDependency(buildRoot: BuildRoot): F[Option[Scope.Dependencies]] =
    OptionT(getScalafmtVersion(buildRoot))
      .map(version => Scope(List(scalafmtDependency(version)), List(config.defaultResolver)))
      .value

  def version: F[String] =
    workspaceAlg.rootDir
      .flatMap(processAlg.exec(Nel.of(scalafmtBinary, "--version"), _))
      .map(_.mkString.trim)
}

object ScalafmtAlg {
  private[scalafmt] def parseScalafmtConf(s: String): Option[Version] =
    io.circe.config.parser
      .parse(s)
      .toOption
      .flatMap(_.asObject)
      .flatMap(_.apply("version"))
      .flatMap(_.asString)
      .map(Version.apply)
}
