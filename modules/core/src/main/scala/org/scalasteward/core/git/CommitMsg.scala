/*
 * Copyright 2018-2022 Scala Steward contributors
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

package org.scalasteward.core.git

import cats.syntax.all._
import org.scalasteward.core.data.Update
import org.scalasteward.core.update.show
import org.scalasteward.core.util.Nel

final case class CommitMsg(
    title: String,
    body: List[String] = Nil,
    coAuthoredBy: List[Author] = Nil
) {
  def toNel: Nel[String] =
    Nel(title, body ++ trailers)

  def withParagraph(paragraph: String): CommitMsg =
    copy(body = body :+ paragraph)

  private def trailers: Option[String] = {
    val lines = coAuthoredBy.map(author => s"Co-authored-by: ${author.show}")
    Nel.fromList(lines).map(_.mkString_("\n"))
  }
}

object CommitMsg {
  def replaceVariables(s: String)(update: Update, baseBranch: Option[Branch]): CommitMsg = {
    val artifactNameValue = show.oneLiner(update)
    val nextVersionValue = update.nextVersion.value
    val defaultValue = s"Update $artifactNameValue to $nextVersionValue" +
      baseBranch.fold("")(branch => s" in ${branch.name}")
    val title = s
      .replace("${default}", defaultValue)
      .replace("${artifactName}", artifactNameValue)
      .replace("${currentVersion}", update.currentVersion.value)
      .replace("${nextVersion}", nextVersionValue)
      .replace("${branchName}", baseBranch.map(_.name).orEmpty)
    CommitMsg(title = title)
  }
}
