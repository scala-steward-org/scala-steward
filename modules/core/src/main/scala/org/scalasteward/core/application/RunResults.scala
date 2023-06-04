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

package org.scalasteward.core.application

import cats.effect.ExitCode
import cats.syntax.all._
import org.scalasteward.core.data.Repo

case class RunResults(results: List[Either[(Repo, Throwable), Repo]]) {
  val (reposWithFailures, successRepos) = results.separate

  val exitCode: ExitCode = if (reposWithFailures.isEmpty) ExitCode.Success else ExitCode.Error

  val markdownSummary: String = {
    val failuresSummaryOpt = Option.when(reposWithFailures.nonEmpty) {
      (Seq(s"# Job failed for ${reposWithFailures.size} out of ${results.size} repos") ++ (for {
        (repo, failure) <- reposWithFailures
      } yield s"""#### ❌ ${repo.show}
                 |<details>
                 |<summary>Full error</summary>
                 |
                 |```
                 |$failure
                 |```
                 |</details>""".stripMargin)).mkString("\n\n")
    }

    val successSummary =
      s"""# Job successfully processed ${successRepos.size} repos
         |
         |""".stripMargin + (successRepos.map(repo => s"* ✅ ${repo.show}").mkString("\n"))

    (failuresSummaryOpt.toSeq :+ successSummary).mkString("\n\n")
  }
}
