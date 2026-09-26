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

package org.scalasteward.core.repocache

import cats.syntax.all.*
import munit.CatsEffectSuite
import org.scalasteward.core.data.Repo
import org.scalasteward.core.mock.MockContext.context.refreshErrorAlg
import org.scalasteward.core.mock.{MockEff, MockEffOps, MockState}

class RefreshErrorAlgTest extends CatsEffectSuite {
  test("throwIfFailedRecently: not failed") {
    val repo = Repo("refresh-error", "test-1")
    val p = refreshErrorAlg.throwIfFailedRecently(repo)

    val expected = "()"
    p.runA(MockState.empty).attempt.map { obtained =>
      val obtainedStr = obtained.fold(_.getMessage, _.toString).take(expected.length)
      assertEquals(obtainedStr, expected)
    }
  }

  test("throwIfFailedRecently: failed") {
    val repo = Repo("refresh-error", "test-2")
    val p = refreshErrorAlg.persistError(repo)(MockEff.raiseError(new Throwable())).attempt >>
      refreshErrorAlg.throwIfFailedRecently(repo)

    val expected = "Skipping due to previous error"
    p.runA(MockState.empty).attempt.map { obtained =>
      val obtainedStr = obtained.fold(_.getMessage, _.toString).take(expected.length)
      assertEquals(obtainedStr, expected)
    }
  }
}
