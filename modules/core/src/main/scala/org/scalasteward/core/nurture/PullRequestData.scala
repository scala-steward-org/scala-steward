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

package org.scalasteward.core.nurture

import org.http4s.Uri
import org.scalasteward.core.data.Update
import org.scalasteward.core.forge.data.{PullRequestNumber, PullRequestState}
import org.scalasteward.core.git.{Branch, Sha1}

final case class PullRequestData[F[_]](
    url: Uri,
    baseSha1: Sha1,
    update: Update,
    state: PullRequestState,
    number: F[PullRequestNumber],
    updateBranch: F[Branch]
)
