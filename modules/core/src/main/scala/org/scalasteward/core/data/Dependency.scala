/*
 * Copyright 2018-2025 Scala Steward contributors
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

package org.scalasteward.core.data

import cats.Order
import io.circe.Codec
import io.circe.generic.semiauto.*
import org.scalasteward.core.buildtool.sbt.data.{SbtVersion, ScalaVersion}

final case class Dependency(
    groupId: GroupId,
    artifactId: ArtifactId,
    version: Version,
    sbtVersion: Option[SbtVersion] = None,
    scalaVersion: Option[ScalaVersion] = None,
    configurations: Option[String] = None
) {
  def attributes: Map[String, String] =
    sbtVersion.map("sbtVersion" -> _.value).toMap ++
      scalaVersion.map("scalaVersion" -> _.value).toMap
}

object Dependency {
  implicit val dependencyCodec: Codec[Dependency] =
    deriveCodec

  implicit val dependencyOrder: Order[Dependency] =
    Order.by { (d: Dependency) =>
      (d.groupId, d.artifactId, d.version, d.sbtVersion, d.scalaVersion, d.configurations)
    }
}
