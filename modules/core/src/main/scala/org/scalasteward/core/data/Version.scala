/*
 * Copyright 2018-2019 Scala Steward contributors
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
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import scala.annotation.tailrec

final case class Version(value: String) {
  val components: List[Version.Component] =
    Version.Component.parse(value)

  /** Selects the next version from a list of potentially newer versions.
    *
    * Implements the scheme described in this FAQ:
    * https://github.com/fthomas/scala-steward/blob/master/docs/faq.md#how-does-scala-steward-decide-what-version-it-is-updating-to
    */
  def selectNext(versions: List[Version]): Option[Version] = {
    val cutoff = preReleaseIndex.fold(components.size)(_.value - 1)
    val newerVersionsByCommonPrefix =
      versions
        .filter(_ > this)
        .groupBy(_.components.zip(components).take(cutoff).takeWhile {
          case (c1, c2) => c1 === c2
        })

    newerVersionsByCommonPrefix.toList
      .sortBy { case (commonPrefix, _) => commonPrefix.length }
      .flatMap {
        case (commonPrefix, vs) =>
          // Do not select pre-release versions of a different series.
          vs.filterNot(_.isPreRelease && cutoff =!= commonPrefix.length).sorted
      }
      .lastOption
  }

  private def isPreRelease: Boolean =
    preReleaseIndex.isDefined

  private def preReleaseIndex: Option[NonNegInt] =
    NonNegInt.unapply(components.indexWhere {
      case Version.Component.Hyphen       => true
      case a @ Version.Component.Alpha(_) => a.order < 0
      case _                              => false
    })
}

object Version {
  implicit val versionOrder: Order[Version] =
    Order.from[Version] { (v1, v2) =>
      val (c1, c2) = padToSameLength(v1.components, v2.components, Component.Empty)
      c1.compare(c2)
    }

  implicit val versionDecoder: Decoder[Version] =
    deriveDecoder

  implicit val versionEncoder: Encoder[Version] =
    deriveEncoder

  private def padToSameLength[A](l1: List[A], l2: List[A], elem: A): (List[A], List[A]) = {
    val maxLength = math.max(l1.length, l2.length)
    (l1.padTo(maxLength, elem), l2.padTo(maxLength, elem))
  }

  sealed trait Component extends Product with Serializable
  object Component {
    final case class Numeric(value: String) extends Component
    final case class Alpha(value: String) extends Component {
      def order: Int = value.toUpperCase match {
        case "SNAP" | "SNAPSHOT" => -5
        case "ALPHA"             => -4
        case "BETA"              => -3
        case "M"                 => -2
        case "RC"                => -1
        case _                   => 0
      }
    }
    case object Dot extends Component
    case object Hyphen extends Component
    case object Plus extends Component
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
              case '.' | '-' | '+' =>
                val sep = if (h === '.') Dot else if (h === '-') Hyphen else Plus
                loop(t, List.empty, List.empty, sep :: materialize(accN, accA, acc))

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
        case Dot            => "."
        case Hyphen         => "-"
        case Plus           => "+"
        case Empty          => ""
      }.mkString

    implicit val componentOrder: Order[Component] =
      Order.from[Component] {
        case (Numeric(v1), Numeric(v2)) => BigInt(v1).compare(BigInt(v2))
        case (Numeric(_), Alpha(_))     => -1
        case (Alpha(_), Numeric(_))     => 1
        case (Numeric(_), _)            => 1
        case (_, Numeric(_))            => -1

        case (a1 @ Alpha(v1), a2 @ Alpha(v2)) =>
          val (o1, o2) = (a1.order, a2.order)
          if (o1 < 0 || o2 < 0) o1.compare(o2) else v1.compare(v2)

        case (Alpha(_), _) => 1
        case (_, Alpha(_)) => -1

        case (Hyphen, Hyphen) => 0
        case (Hyphen, _)      => -1
        case (_, Hyphen)      => 1

        case (Dot, Dot) => 0
        case (Dot, _)   => 1
        case (_, Dot)   => -1

        case (Plus, Plus) => 0
        case (Plus, _)    => 1
        case (_, Plus)    => -1

        case (Empty, Empty) => 0
      }
  }
}
