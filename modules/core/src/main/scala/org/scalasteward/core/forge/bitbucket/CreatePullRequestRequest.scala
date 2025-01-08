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

package org.scalasteward.core.forge.bitbucket

import io.circe.{Encoder, Json}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch

private[bitbucket] case class CreatePullRequestRequest(
    title: String,
    sourceBranch: Branch,
    sourceRepo: Repo,
    destinationBranch: Branch,
    description: String,
    reviewers: List[Reviewer]
)

private[bitbucket] object CreatePullRequestRequest {
  implicit val encoder: Encoder[CreatePullRequestRequest] = Encoder.instance { d =>
    Json.obj(
      ("title", Json.fromString(d.title)),
      (
        "source",
        Json.obj(
          ("branch", Json.obj(("name", Json.fromString(d.sourceBranch.name)))),
          (
            "repository",
            Json.obj(("full_name", Json.fromString(s"${d.sourceRepo.owner}/${d.sourceRepo.repo}")))
          )
        )
      ),
      ("description", Json.fromString(d.description)),
      (
        "destination",
        Json.obj(
          ("branch", Json.obj(("name", Json.fromString(d.destinationBranch.name))))
        )
      ),
      (
        "reviewers",
        Json.fromValues(
          d.reviewers.map(r => Json.obj(("uuid", Json.fromString(r.uuid))))
        )
      )
    )
  }
}
