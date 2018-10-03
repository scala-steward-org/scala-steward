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
import cats.effect.Sync
import cats.implicits._
import eu.timepit.scalasteward.io.{FileAlg, FileData}

package object sbt {
  def addGlobalPlugin[F[_]](plugin: FileData)(implicit F: Sync[F]): F[Unit] = {
    val fileAlg = FileAlg.sync[F]
    val homeF = F.delay(File.home)
    List(".sbt/0.13/plugins", ".sbt/1.0/plugins").traverse_ { path =>
      homeF.flatMap(home => fileAlg.writeFileData(home / path, plugin))
    }
  }

  val defaultSbtVersion: SbtVersion =
    SbtVersion("1.2.3")

  def seriesToSpecificVersion(sbtSeries: SbtVersion): SbtVersion =
    sbtSeries.value match {
      case "0.13" => SbtVersion("0.13.17")
      case "1.0"  => defaultSbtVersion
      case _      => defaultSbtVersion
    }
}
