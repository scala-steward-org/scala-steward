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

package org.scalasteward.core.forge.github

import io.circe.Encoder

class GitHubReviewers private (
    val reviewers: List[String],
    val teamReviewers: List[String]
)

object GitHubReviewers {
  def apply(reviewersFromConfig: List[String]): GitHubReviewers = {
    val (simpleReviewers, teamReviewers) = reviewersFromConfig.partitionMap {
      case s"$_/$team" => Right(team)
      case user        => Left(user)
    }
    new GitHubReviewers(simpleReviewers, teamReviewers)
  }

  implicit val gitHubReviewersEncoder: Encoder[GitHubReviewers] =
    Encoder.forProduct2("reviewers", "team_reviewers")(gitHubReviewers =>
      (gitHubReviewers.reviewers, gitHubReviewers.teamReviewers)
    )
}
