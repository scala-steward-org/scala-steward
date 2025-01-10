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

import munit.FunSuite
import org.scalasteward.core.data.Repo
import scala.io.Source
class RunResultsTest extends FunSuite {
  private val repo1 = Repo("scala-steward-org", "scala-steward")
  private val repo2 = Repo("guardian", "play-secret-rotation")
  private val repo3 = Repo("scala-steward-org", "scala-steward-action")

  test("render markdown summary for an entirely successful run") {
    assertEquals(
      RunResults(
        List(
          Right(repo1),
          Right(repo2)
        )
      ).markdownSummary,
      Source.fromResource("job-summary/entirely-good.md").mkString
    )
  }

  test("render markdown summary for an partially failed run") {
    assertEquals(
      RunResults(
        List(
          Left(repo1 -> new IllegalStateException("Bad thing")),
          Right(repo2),
          Left(repo3 -> new RuntimeException("Also bad thing"))
        )
      ).markdownSummary,
      Source.fromResource("job-summary/partially-failed.md").mkString
    )
  }
}
