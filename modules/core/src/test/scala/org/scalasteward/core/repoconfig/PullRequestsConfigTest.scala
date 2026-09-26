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

package org.scalasteward.core.repoconfig

import cats.implicits.*
import cats.kernel.laws.discipline.MonoidTests
import munit.DisciplineSuite
import org.scalasteward.core.TestInstances.*

class PullRequestsConfigTest extends DisciplineSuite {
  checkAll("Monoid[PullRequestsConfig]", MonoidTests[PullRequestsConfig].monoid)

  test("global config to use 'draft' PRs should be retained after merging with local config") {
    val draftTrue = PullRequestsConfig(draft = Some(true))
    val draftUnset = PullRequestsConfig()
    val draftFalse = PullRequestsConfig(draft = Some(false))

    assert((draftTrue |+| draftUnset).draft === Some(true))
    assert((draftTrue |+| draftFalse).draft === Some(true))
    assert((draftUnset |+| draftUnset).draft === None)
  }
}
