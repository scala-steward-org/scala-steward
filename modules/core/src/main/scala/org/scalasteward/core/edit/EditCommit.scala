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

package org.scalasteward.core.edit

import org.scalasteward.core.data.Update
import org.scalasteward.core.edit.hooks.PostUpdateHook
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.git.Commit

sealed trait EditCommit extends Product with Serializable {
  def commit: Commit
}

object EditCommit {
  final case class UpdateCommit(update: Update, commit: Commit) extends EditCommit
  final case class ScalafixCommit(migration: ScalafixMigration, commit: Commit) extends EditCommit
  final case class HookCommit(hook: PostUpdateHook, commit: Commit) extends EditCommit
}
