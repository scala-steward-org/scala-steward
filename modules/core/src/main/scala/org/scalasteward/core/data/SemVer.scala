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

package org.scalasteward.core.data

import cats.syntax.all._
import org.scalasteward.core.data.SemVer.Change._

import scala.annotation.tailrec

final case class SemVer(
    major: String,
    minor: String,
    patch: String,
    preRelease: Option[String] = None,
    buildMetadata: Option[String] = None
)

object SemVer {
  def parse(s: String): Option[SemVer] =
    cats.parse.SemVer.semver.parseAll(s).toOption.map { v =>
      SemVer(v.core.major, v.core.minor, v.core.patch, v.preRelease, v.buildMetadata)
    }

  sealed abstract class Change(val render: String)
  object Change {
    case object Major extends Change("major")
    case object Minor extends Change("minor")
    case object Patch extends Change("patch")
    case object PreRelease extends Change("pre-release")
    case object BuildMetadata extends Change("build-metadata")
  }

  def getChangeSpec(from: SemVer, to: SemVer): Option[Change] =
    if (from.major =!= to.major) Some(Major)
    else if (from.minor =!= to.minor) Some(Minor)
    else if (from.preRelease =!= to.preRelease) Some(PreRelease)
    else if (from.patch =!= to.patch) Some(Patch)
    else if (from.buildMetadata =!= to.buildMetadata) Some(BuildMetadata)
    else None

  @tailrec
  def getChangeEarly(from: SemVer, to: SemVer): Option[Change] = {
    val zero = "0"
    // Codacy doesn't allow using `if`s, so using `match` instead
    (from.major === zero, to.major === zero) match { // work around Codacy's "Consider using case matching instead of else if blocks"
      case (true, true)
          if from.minor =!= zero ||
            to.minor =!= zero ||
            from.patch =!= zero ||
            to.patch =!= zero =>
        getChangeEarly(
          from.copy(major = from.minor, minor = from.patch, patch = zero),
          to.copy(major = to.minor, minor = to.patch, patch = zero)
        )
      case _ =>
        getChangeSpec(from, to)
    }
  }
}
