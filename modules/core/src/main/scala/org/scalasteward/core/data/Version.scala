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

package org.scalasteward.core.data

import cats.Order
import cats.implicits._
import cats.parse.{Numbers, Parser, Rfc5234}
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import org.scalasteward.core.data.Version.startsWithDate

final case class Version(value: String) {
  override def toString: String = value

  private val components: List[Version.Component] =
    Version.Component.parse(value)

  val alnumComponents: List[Version.Component] =
    components.filter(_.isAlphanumeric)

  /** Selects the next version from a list of potentially newer versions.
    *
    * Implements the scheme described in this FAQ:
    * https://github.com/scala-steward-org/scala-steward/blob/main/docs/faq.md#how-does-scala-steward-decide-what-version-it-is-updating-to
    */
  def selectNext(versions: List[Version], allowPreReleases: Boolean = false): Option[Version] = {
    val cutoff = alnumComponentsWithoutPreRelease.length - 1
    val newerVersionsByCommonPrefix =
      if (this.isPreRelease && allowPreReleases) {
        versions.groupBy(_ => List.empty)
      } else {
        versions
          .filter(_ > this)
          .groupBy(_.alnumComponents.zip(alnumComponents).take(cutoff).takeWhile { case (c1, c2) =>
            c1 === c2
          })
      }

    newerVersionsByCommonPrefix.toList
      .sortBy { case (commonPrefix, _) => commonPrefix.length }
      .flatMap { case (commonPrefix, vs) =>
        val sameSeries = cutoff === commonPrefix.length

        val preReleasesFilter: Version => Boolean = v =>
          // Do not select snapshots if we are not already on snapshot
          v.isSnapshot && !isSnapshot

        val releasesFilter: Version => Boolean = v =>
          // Do not select pre-releases of different series.
          (v.isPreRelease && !sameSeries) ||
            // Do not select pre-releases of the same series if this is not a pre-release.
            (v.isPreRelease && !isPreRelease && sameSeries) ||
            // Do not select versions with pre-release identifiers whose order is smaller
            // than the order of possible pre-release identifiers in this version. This,
            // for example, prevents updates from 2.1.4.0-RC17 to 2.1.4.0-RC17+1-307f2f6c-SNAPSHOT.
            (v.minAlphaOrder < minAlphaOrder) ||
            // Do not select a version with hash if this version contains no hash.
            (v.containsHash && !containsHash)

        val commonFilter: Version => Boolean = v =>
          // Do not select versions that are identical up to the hashes.
          v.alnumComponents === alnumComponents ||
            // Don't select "versions" like %5BWARNING%5D.
            !v.startsWithLetterOrDigit

        vs.filterNot { v =>
          commonFilter(v) ||
          (!allowPreReleases && releasesFilter(v)) ||
          (allowPreReleases && preReleasesFilter(v))
        }.sorted
      }
      .lastOption
  }

  private def startsWithLetterOrDigit: Boolean =
    components.headOption.forall {
      case _: Version.Component.Numeric => true
      case a: Version.Component.Alpha   => a.value.headOption.forall(_.isLetter)
      case _                            => false
    }

  private def isPreRelease: Boolean =
    components.exists {
      case a: Version.Component.Alpha => a.isPreReleaseIdent
      case _: Version.Component.Hash  => true
      case _                          => false
    }

  private def isSnapshot: Boolean =
    components.exists {
      case a: Version.Component.Alpha => a.isSnapshotIdent
      case _: Version.Component.Hash  => true
      case _                          => false
    }

  private def containsHash: Boolean =
    components.exists {
      case _: Version.Component.Hash => true
      case _                         => false
    } || Rfc5234.hexdig.rep(8).string.filterNot(startsWithDate).parse(value).isRight

  private[this] def alnumComponentsWithoutPreRelease: List[Version.Component] =
    alnumComponents.takeWhile {
      case a: Version.Component.Alpha => !a.isPreReleaseIdent
      case _                          => true
    }

  private val minAlphaOrder: Int =
    alnumComponents.collect { case a: Version.Component.Alpha => a.order }.minOption.getOrElse(0)
}

object Version {
  case class Update(currentVersion: Version, nextVersion: Version)

  val tagNames: List[Version => String] = List("v" + _, _.value, "release-" + _)

  implicit val versionCodec: Codec[Version] =
    deriveUnwrappedCodec

  implicit val versionOrder: Order[Version] =
    Order.from[Version] { (v1, v2) =>
      val (c1, c2) = padToSameLength(v1.alnumComponents, v2.alnumComponents, Component.Empty)
      c1.compare(c2)
    }

  private def padToSameLength[A](l1: List[A], l2: List[A], elem: A): (List[A], List[A]) = {
    val maxLength = math.max(l1.length, l2.length)
    (l1.padTo(maxLength, elem), l2.padTo(maxLength, elem))
  }

  private def startsWithDate(s: String): Boolean =
    s.length >= 8 && s.substring(0, 8).forall(_.isDigit) && {
      val year = s.substring(0, 4).toInt
      val month = s.substring(4, 6).toInt
      val day = s.substring(6, 8).toInt
      (year >= 1900 && year <= 2100) &&
      (month >= 1 && month <= 12) &&
      (day >= 1 && day <= 31)
    }

  sealed trait Component extends Product with Serializable {
    final def isAlphanumeric: Boolean =
      this match {
        case _: Component.Numeric => true
        case _: Component.Alpha   => true
        case _                    => false
      }
  }
  object Component {
    final case class Numeric(value: String) extends Component {
      def toBigInt: BigInt = BigInt(value)
    }
    final case class Alpha(value: String) extends Component {
      def isPreReleaseIdent: Boolean = order < 0
      def isSnapshotIdent: Boolean = order <= -7
      def order: Int =
        value.toUpperCase match {
          case "SNAP" | "SNAPSHOT" | "NIGHTLY" => -7
          case "FEAT" | "FEATURE"              => -6
          case "ALPHA" | "PREVIEW"             => -5
          case "BETA" | "B"                    => -4
          case "EA" /* early access */         => -3
          case "M" | "MILESTONE" | "AM"        => -2
          case "RC"                            => -1
          case _                               => 0
        }
    }
    final case class Hash(value: String) extends Component
    final case class Separator(c: Char) extends Component
    case object Empty extends Component

    private val componentsParser = {
      val digits = ('0' to '9').toSet
      val separators = Set('.', '-', '_', '+')

      val numeric = Numbers.digits.map(s => List(Numeric(s)))
      val alpha = Parser.charsWhile(c => !digits(c) && !separators(c)).map(s => List(Alpha(s)))
      val separator = Parser.charIn(separators).map(c => List(Separator(c)))
      val hash = (Parser.charIn('-', '+') ~
        Parser.char('g').string.? ~
        Rfc5234.hexdig.rep(6).string.filterNot(startsWithDate)).backtrack
        .map { case ((s, g), h) => List(Separator(s), Hash(g.getOrElse("") + h)) }

      (numeric | alpha | hash | separator).rep0.map(_.flatten)
    }

    def parse(str: String): List[Component] =
      componentsParser.parseAll(str).getOrElse(List.empty)

    def render(components: List[Component]): String =
      components.map {
        case n: Numeric   => n.value
        case a: Alpha     => a.value
        case h: Hash      => h.value
        case s: Separator => s.c.toString
        case Empty        => ""
      }.mkString

    // This is similar to https://get-coursier.io/docs/other-version-handling.html#ordering
    // but not exactly the same ordering as used by Coursier. One difference is that we are
    // using different pre-release identifiers.
    implicit val componentOrder: Order[Component] =
      Order.from[Component] {
        case (n1: Numeric, n2: Numeric) => n1.toBigInt.compare(n2.toBigInt)
        case (_: Numeric, _)            => 1
        case (_, _: Numeric)            => -1

        case (a1: Alpha, a2: Alpha) =>
          val (o1, o2) = (a1.order, a2.order)
          if (o1 < 0 || o2 < 0) o1.compare(o2) else a1.value.compare(a2.value)

        case (_: Alpha, Empty) => -1
        case (Empty, _: Alpha) => 1

        case _ => 0
      }
  }
}
