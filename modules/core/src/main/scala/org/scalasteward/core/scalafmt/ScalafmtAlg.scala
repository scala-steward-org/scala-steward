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

package org.scalasteward.core.scalafmt

import cats.Monad
import cats.data.OptionT
import cats.syntax.all._
import io.circe.ParsingFailure
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.{Scope, Version}
import org.scalasteward.core.io.process.SlurpOptions
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.scalafmt.ScalafmtAlg.{opts, parseScalafmtConf}
import org.scalasteward.core.util.Nel
import org.typelevel.log4cats.Logger

final class ScalafmtAlg[F[_]](config: Config)(implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    processAlg: ProcessAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  def getScalafmtVersion(buildRoot: BuildRoot): F[Option[Version]] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      scalafmtConfFile = buildRootDir / scalafmtConfName
      fileContent <- fileAlg.readFile(scalafmtConfFile)
      version <- fileContent.map(parseScalafmtConf).fold(F.pure(Option.empty[Version])) {
        case Left(error)    => logger.warn(error)(s"Failed to parse $scalafmtConfName").as(None)
        case Right(version) => F.pure(version)
      }
    } yield version

  def getScopedScalafmtDependency(buildRoot: BuildRoot): F[Option[Scope.Dependencies]] =
    OptionT(getScalafmtVersion(buildRoot))
      .map(version => Scope(List(scalafmtDependency(version)), List(config.defaultResolver)))
      .value

  def reformatChanged(buildRoot: BuildRoot): F[Unit] =
    for {
      buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
      cmd = Nel.of(scalafmtBinary, opts.nonInteractive) ++ opts.modeChanged
      _ <- processAlg.exec(cmd, buildRootDir, slurpOptions = SlurpOptions.ignoreBufferOverflow)
    } yield ()

  def version: F[String] = {
    val cmd = Nel.of(scalafmtBinary, opts.version)
    workspaceAlg.rootDir.flatMap(processAlg.exec(cmd, _)).map(_.mkString.trim)
  }
}

object ScalafmtAlg {
  object opts {
    val modeChanged: List[String] = List("--mode", "changed")
    val nonInteractive = "--non-interactive"
    val version = "--version"
  }

  val postUpdateHookCommand: Nel[String] =
    Nel.of(scalafmtBinary, opts.nonInteractive)

  private[scalafmt] def parseScalafmtConf(s: String): Either[ParsingFailure, Option[Version]] =
    io.circe.config.parser.parse(s).map {
      _.asObject.flatMap(_.apply("version")).flatMap(_.asString).map(Version.apply)
    }
}
