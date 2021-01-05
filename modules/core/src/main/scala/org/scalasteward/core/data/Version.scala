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

package org.scalasteward.core.data

import cats.Order
import cats.implicits._
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec

final case class Version(value: String) {
  private val components: List[Version.Component] =
    Version.Component.parse(value)

  val alnumComponents: List[Version.Component] =
    components.filter(_.isAlphanumeric)

  /** Selects the next version from a list of potentially newer versions.
    *
    * Implements the scheme described in this FAQ:
    * https://github.com/scala-steward-org/scala-steward/blob/master/docs/faq.md#how-does-scala-steward-decide-what-version-it-is-updating-to
    */
  def selectNext(versions: List[Version]): Option[Version] = {
    val cutoff = alnumComponentsWithoutPreRelease.length - 1
    val newerVersionsByCommonPrefix =
      versions
        .filter(_ > this)
        .groupBy(_.alnumComponents.zip(alnumComponents).take(cutoff).takeWhile { case (c1, c2) =>
          c1 === c2
        })

    newerVersionsByCommonPrefix.toList
      .sortBy { case (commonPrefix, _) => commonPrefix.length }
      .flatMap { case (commonPrefix, vs) =>
        val sameSeries = cutoff === commonPrefix.length
        vs.filterNot { v =>
          // Do not select pre-releases of different series.
          (v.isPreRelease && !sameSeries) ||
          // Do not select pre-releases of the same series if this is not a pre-release.
          (v.isPreRelease && !isPreRelease && sameSeries) ||
          // Do not select versions with pre-release identifiers whose order is smaller
          // than the order of pre-release identifiers in this version. This, for example,
          // prevents updates from 2.1.4.0-RC17 to 2.1.4.0-RC17+1-307f2f6c-SNAPSHOT.
          ((minAlphaOrder < 0) && (v.minAlphaOrder < minAlphaOrder)) ||
          // Do not select versions that are identical up to the hashes.
          v.alnumComponents === alnumComponents ||
          // Do not select a version with hash if this version contains no hash.
          (v.containsHash && !containsHash) ||
          // Don't select "versions" like %5BWARNING%5D.
          !v.startsWithLetterOrDigit
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

  private def containsHash: Boolean =
    components.exists {
      case _: Version.Component.Hash => true
      case _                         => false
    }

  private[this] def alnumComponentsWithoutPreRelease: List[Version.Component] =
    alnumComponents.takeWhile {
      case a: Version.Component.Alpha => !a.isPreReleaseIdent
      case _                          => true
    }

  private val minAlphaOrder: Int =
    alnumComponents.collect { case a: Version.Component.Alpha => a.order }.minOption.getOrElse(0)
}

object Version {
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
    """(\d{4})(\d{2})(\d{2})""".r.findPrefixMatchOf(s).exists { m =>
      val year = m.group(1).toInt
      val month = m.group(2).toInt
      val day = m.group(3).toInt
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
      def order: Int =
        value.toUpperCase match {
          case "SNAP" | "SNAPSHOT" | "NIGHTLY" => -5
          case "ALPHA" | "PREVIEW"             => -4
          case "BETA" | "B"                    => -3
          case "M" | "MILESTONE" | "AM"        => -2
          case "RC"                            => -1
          case _                               => 0
        }
    }
    final case class Hash(value: String) extends Component
    final case class Separator(c: Char) extends Component
    case object Empty extends Component

    private val numeric = """^(\d+)(.*)$""".r
    private val separator = """^([.\-_+])(.*)$""".r
    private val alpha = """^([^.\-_+\d]+)(.*)$""".r
    private val hash = """^([-+])(g?\p{XDigit}{6,})(.*)$""".r

    def parse(str: String): List[Component] =
      str match {
        case "" => List.empty
        case hash(sep, value, rest) if !startsWithDate(value) =>
          Separator(sep.head) +: Hash(value) +: parse(rest)
        case numeric(value, rest) =>
          Numeric(value) +: parse(rest)
        case alpha(value, rest) =>
          Alpha(value) +: parse(rest)
        case separator(value, rest) =>
          Separator(value.head) +: parse(rest)
      }

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
