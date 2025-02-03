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

package org.scalasteward.core.edit.scalafix

import cats.Eq
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.generic.semiauto.*
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.edit.scalafix.ScalafixMigration.{ExecutionOrder, Target}
import org.scalasteward.core.git.{Author, CommitMsg}
import org.scalasteward.core.util.{unexpectedString, Nel}

final case class ScalafixMigration(
    groupId: GroupId,
    artifactIds: Nel[String],
    newVersion: Version,
    rewriteRules: Nel[String],
    doc: Option[String] = None,
    scalacOptions: Option[Nel[String]] = None,
    authors: Option[Nel[Author]] = None,
    target: Option[Target] = None,
    executionOrder: Option[ExecutionOrder] = None
) {
  def commitMessage(result: Either[Throwable, Unit]): CommitMsg = {
    val verb = if (result.isRight) "Applied" else "Failed"
    val title = s"$verb Scalafix rule(s) ${rewriteRules.mkString_(", ")}"
    val body = doc.map(url => s"See $url for details")
    CommitMsg(title, body.toList, authors.foldMap(_.toList))
  }

  def executionOrderOrDefault: ExecutionOrder =
    executionOrder.getOrElse(ExecutionOrder.PreUpdate)

  def targetOrDefault: Target =
    target.getOrElse(Target.Sources)
}

object ScalafixMigration {
  sealed trait ExecutionOrder extends Product with Serializable
  object ExecutionOrder {
    case object PreUpdate extends ExecutionOrder
    case object PostUpdate extends ExecutionOrder

    implicit val executionOrderEq: Eq[ExecutionOrder] =
      Eq.fromUniversalEquals

    implicit val executionOrderDecoder: Decoder[ExecutionOrder] =
      Decoder[String].emap {
        case "pre-update"  => Right(PreUpdate)
        case "post-update" => Right(PostUpdate)
        case s             => unexpectedString(s, List("pre-update", "post-update"))
      }
  }

  sealed trait Target extends Product with Serializable
  object Target {
    case object Sources extends Target
    case object Build extends Target

    implicit val targetDecoder: Decoder[Target] =
      Decoder[String].emap {
        case "sources" => Right(Sources)
        case "build"   => Right(Build)
        case s         => unexpectedString(s, List("sources", "build"))
      }
  }

  implicit val scalafixMigrationDecoder: Decoder[ScalafixMigration] =
    deriveDecoder
}
