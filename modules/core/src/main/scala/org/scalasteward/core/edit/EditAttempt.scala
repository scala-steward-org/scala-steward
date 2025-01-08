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

package org.scalasteward.core.edit

import org.scalasteward.core.data.Update
import org.scalasteward.core.edit.hooks.PostUpdateHook
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.git.Commit

sealed trait EditAttempt extends Product with Serializable {
  def commits: List[Commit]
}

object EditAttempt {
  final case class UpdateEdit(update: Update.Single, commit: Commit) extends EditAttempt {
    override def commits: List[Commit] = List(commit)
  }

  final case class ScalafixEdit(
      migration: ScalafixMigration,
      result: Either[Throwable, Unit],
      maybeCommit: Option[Commit]
  ) extends EditAttempt {
    override def commits: List[Commit] = maybeCommit.toList
  }

  final case class HookEdit(
      hook: PostUpdateHook,
      result: Either[Throwable, Unit],
      commits: List[Commit]
  ) extends EditAttempt
}
