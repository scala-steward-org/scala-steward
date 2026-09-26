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

package org.scalasteward.core.forge.data

import munit.FunSuite
import org.scalasteward.core.data.Repo
import org.scalasteward.core.git.Branch

class RepoTest extends FunSuite {
  test("parse") {
    assertEquals(Repo.parse("- typelevel/cats-effect"), Some(Repo("typelevel", "cats-effect")))
    assertEquals(Repo.parse("- group1/group2/project1"), Some(Repo("group1/group2", "project1")))
    assertEquals(
      Repo.parse("- typelevel/cats-effect:3.x"),
      Some(Repo("typelevel", "cats-effect", Some(Branch("3.x"))))
    )
    assertEquals(
      Repo.parse("- typelevel/cats-effect:series/3.x"),
      Some(Repo("typelevel", "cats-effect", Some(Branch("series/3.x"))))
    )

    assertEquals(Repo.parse("typelevel/cats-effect"), None)
    assertEquals(Repo.parse("- typelevel-cats-effect"), None)
  }
}
