/*
 * Copyright 2018-2020 Scala Steward contributors
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
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import scala.annotation.tailrec

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
        .groupBy(_.alnumComponents.zip(alnumComponents).take(cutoff).takeWhile {
          case (c1, c2) => c1 === c2
        })

    newerVersionsByCommonPrefix.toList
      .sortBy { case (commonPrefix, _) => commonPrefix.length }
      .flatMap {
        case (commonPrefix, vs) =>
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
            // Don't select "versions" like %5BWARNING%5D.
            !v.startsWithLetterOrDigit
          }.sorted
      }
      .lastOption
  }

  private def startsWithLetterOrDigit: Boolean =
    components.headOption.forall {
      case Version.Component.Numeric(_)   => true
      case Version.Component.Alpha(value) => value.headOption.forall(_.isLetter)
      case _                              => false
    }

  private def isPreRelease: Boolean =
    preReleaseIndex.isDefined

  private[this] def alnumComponentsWithoutPreRelease: List[Version.Component] =
    preReleaseIndex
      .map(i => Version.Component.parse(value.substring(0, i.value)).filter(_.isAlphanumeric))
      .getOrElse(alnumComponents)

  private[this] val preReleaseIndex: Option[NonNegInt] = {
    val preReleaseIdentIndex = NonNegInt.unapply(components.indexWhere {
      case a @ Version.Component.Alpha(_) => a.isPreReleaseIdent
      case _                              => false
    })
    preReleaseIdentIndex
      .map(i => NonNegInt.unsafeFrom(components.take(i.value).foldMap(_.length)))
      .orElse(hashIndex)
  }

  private[this] def hashIndex: Option[NonNegInt] =
    """[-+]\p{XDigit}{6,}""".r.findFirstMatchIn(value).flatMap(m => NonNegInt.unapply(m.start))

  private val minAlphaOrder: Int =
    alnumComponents
      .collect { case a @ Version.Component.Alpha(_) => a.order }
      .minOption
      .getOrElse(0)
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

  sealed trait Component extends Product with Serializable {
    final def isAlphanumeric: Boolean =
      this match {
        case Component.Numeric(_) => true
        case Component.Alpha(_)   => true
        case _                    => false
      }

    final def length: Int =
      this match {
        case Component.Numeric(value) => value.length
        case Component.Alpha(value)   => value.length
        case Component.Separator(_)   => 1
        case Component.Empty          => 0
      }
  }
  object Component {
    final case class Numeric(value: String) extends Component {
      def isZero: Boolean = BigInt(value) === BigInt(0)
    }
    final case class Alpha(value: String) extends Component {
      def isPreReleaseIdent: Boolean = order < 0
      def order: Int =
        value.toUpperCase match {
          case "SNAP" | "SNAPSHOT"      => -5
          case "ALPHA" | "PREVIEW"      => -4
          case "BETA" | "B"             => -3
          case "M" | "MILESTONE" | "AM" => -2
          case "RC"                     => -1
          case _                        => 0
        }
    }
    final case class Separator(c: Char) extends Component
    case object Empty extends Component

    def parse(str: String): List[Component] = {
      @tailrec
      def loop(
          rest: List[Char],
          accN: List[Char],
          accA: List[Char],
          acc: List[Component]
      ): List[Component] =
        rest match {
          case h :: t =>
            h match {
              case '.' | '-' | '_' | '+' =>
                loop(t, List.empty, List.empty, Separator(h) :: materialize(accN, accA, acc))

              case _ if h.isDigit && accA.nonEmpty =>
                loop(t, h :: accN, List.empty, materialize(List.empty, accA, acc))

              case _ if h.isDigit && accA.isEmpty =>
                loop(t, h :: accN, List.empty, acc)

              case _ if accN.nonEmpty =>
                loop(t, List.empty, h :: accA, materialize(accN, List.empty, acc))

              case _ if accN.isEmpty =>
                loop(t, List.empty, h :: accA, acc)
            }
          case Nil => materialize(accN, accA, acc)
        }

      def materialize(accN: List[Char], accA: List[Char], acc: List[Component]): List[Component] =
        if (accN.nonEmpty) Numeric(accN.reverse.mkString) :: acc
        else if (accA.nonEmpty) Alpha(accA.reverse.mkString) :: acc
        else acc

      loop(str.toList, List.empty, List.empty, List.empty).reverse
    }

    def render(components: List[Component]): String =
      components.map {
        case Numeric(value) => value
        case Alpha(value)   => value
        case Separator(c)   => c.toString
        case Empty          => ""
      }.mkString

    // This is similar to https://get-coursier.io/docs/other-version-handling.html#ordering
    // but not exactly the same ordering as used by Coursier. One difference is that we are
    // using different pre-release identifiers.
    implicit val componentOrder: Order[Component] =
      Order.from[Component] {
        case (Numeric(v1), Numeric(v2))     => BigInt(v1).compare(BigInt(v2))
        case (n @ Numeric(_), a @ Alpha(_)) => if (a.isPreReleaseIdent || !n.isZero) 1 else -1
        case (a @ Alpha(_), n @ Numeric(_)) => if (a.isPreReleaseIdent || !n.isZero) -1 else 1
        case (Numeric(_), _)                => 1
        case (_, Numeric(_))                => -1

        case (a1 @ Alpha(v1), a2 @ Alpha(v2)) =>
          val (o1, o2) = (a1.order, a2.order)
          if (o1 < 0 || o2 < 0) o1.compare(o2) else v1.compare(v2)

        case (Alpha(_), Empty) => -1
        case (Empty, Alpha(_)) => 1

        case (Alpha(_), _) => 1
        case (_, Alpha(_)) => -1

        case _ => 0
      }
  }
}
