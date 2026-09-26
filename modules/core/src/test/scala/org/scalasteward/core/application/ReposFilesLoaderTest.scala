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

package org.scalasteward.core.application

import munit.CatsEffectSuite
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.context.reposFilesLoader
import org.scalasteward.core.mock.{MockConfig, MockEffOps, MockState}
import org.scalasteward.core.util.Nel

class ReposFilesLoaderTest extends CatsEffectSuite {
  test("non-empty repos file") {
    val initialState = MockState.empty.addUris(MockConfig.reposFile -> "- a/b\n- c/d:e")
    val obtained =
      reposFilesLoader.loadAll(Nel.one(MockConfig.reposFile)).compile.toList.runA(initialState)
    assertIO(obtained, List(Repo("a", "b"), Repo("c", "d", Some(Branch("e")))))
  }

  test("malformed repos file") {
    val initialState = MockState.empty.addUris(MockConfig.reposFile -> " - a/b")
    val obtained =
      reposFilesLoader.loadAll(Nel.one(MockConfig.reposFile)).compile.toList.runA(initialState)
    assertIO(obtained, List.empty)
  }

  test("non-existing repos file") {
    val initialState = MockState.empty
    val obtained =
      reposFilesLoader.loadAll(Nel.one(MockConfig.reposFile)).compile.toList.runA(initialState)
    assertIO(obtained.attempt.map(_.isLeft), true)
  }
}
