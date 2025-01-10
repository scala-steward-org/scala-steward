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

import io.circe.Decoder
import org.http4s.Uri
import org.scalasteward.core.forge.data.*
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.util.unexpectedString
import org.scalasteward.core.util.uri.uriDecoder

private[bitbucket] object json {
  implicit val branchOutDecoder: Decoder[BranchOut] = Decoder.instance { c =>
    for {
      branch <- c.downField("name").as[Branch]
      commitHash <- c.downField("target").downField("hash").as[Sha1]
    } yield BranchOut(branch, CommitOut(commitHash))
  }

  implicit val pullRequestStateDecoder: Decoder[PullRequestState] =
    Decoder[String].emap {
      case "OPEN"                               => Right(PullRequestState.Open)
      case "MERGED" | "SUPERSEDED" | "DECLINED" => Right(PullRequestState.Closed)
      case s => unexpectedString(s, List("OPEN", "MERGED", "SUPERSEDED", "DECLINED"))
    }

  implicit val pullRequestOutDecoder: Decoder[PullRequestOut] = Decoder.instance { c =>
    for {
      id <- c.downField("id").as[PullRequestNumber]
      title <- c.downField("title").as[String]
      state <- c.downField("state").as[PullRequestState]
      html_url <- c.downField("links").downField("html").downField("href").as[Uri]
    } yield PullRequestOut(html_url, state, id, title)
  }
}
