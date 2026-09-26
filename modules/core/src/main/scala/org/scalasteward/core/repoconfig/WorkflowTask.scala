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

package org.scalasteward.core.repoconfig

import cats.Eq
import io.circe.{Decoder, Encoder}
import org.apache.commons.lang3.StringUtils

sealed trait WorkflowTask {
  def name: String
  def taskName: String = s"githubWorkflow${StringUtils.capitalize(name)}"
}

object WorkflowTask {
  case object Generate extends WorkflowTask { val name = "generate" }
  case object Update extends WorkflowTask { val name = "update" }

  // To switch to Update once sbt-typelevel ships the task
  // and pre-0.32.0 versions of sbt-github-actions age out
  val default: WorkflowTask = Generate

  def fromString(value: String): WorkflowTask =
    value.trim.toLowerCase match {
      case Generate.name => Generate
      case Update.name   => Update
      case _             => default
    }

  implicit val workflowTaskDecoder: Decoder[WorkflowTask] =
    Decoder[String].map(fromString)

  implicit val workflowTaskEncoder: Encoder[WorkflowTask] =
    Encoder[String].contramap(_.name)

  implicit val workflowTaskEq: Eq[WorkflowTask] =
    Eq.fromUniversalEquals
}
