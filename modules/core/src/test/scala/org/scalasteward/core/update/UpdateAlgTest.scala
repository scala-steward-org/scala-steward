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

package org.scalasteward.core.update

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.util.Nel

class UpdateAlgTest extends FunSuite {
  test("isUpdateFor") {
    val dependency = ("io.circe".g % ("circe-refined", "circe-refined_2.12").a % "0.11.2").cross
    val update = ("io.circe".g %%
      Nel.of(
        Nel.of(("circe-core", "circe-core_2.12").a),
        Nel.of(
          ("circe-refined", "circe-refined_2.12").a,
          ("circe-refined", "circe-refined_sjs0.6_2.12").a
        )
      ) % "0.11.2" %> "0.12.3").group
    assert(UpdateAlg.isUpdateFor(update, dependency))
  }
}
