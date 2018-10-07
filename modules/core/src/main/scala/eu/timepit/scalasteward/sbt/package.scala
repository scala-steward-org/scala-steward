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

import cats.effect.Sync
import eu.timepit.scalasteward.io.FileData
import scala.io.Source

package object sbt {
  val defaultSbtVersion: SbtVersion =
    SbtVersion("1.2.3")

  def seriesToSpecificVersion(sbtSeries: SbtVersion): SbtVersion =
    sbtSeries.value match {
      case "0.13" => SbtVersion("0.13.17")
      case "1.0"  => defaultSbtVersion
      case _      => defaultSbtVersion
    }

  val sbtUpdatesPlugin: FileData =
    FileData("sbt-updates.sbt", """addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")""")

  def stewardPlugin[F[_]](implicit F: Sync[F]): F[FileData] =
    F.delay {
      val name = "StewardPlugin.scala"
      FileData(name, Source.fromResource(name).mkString)
    }
}
