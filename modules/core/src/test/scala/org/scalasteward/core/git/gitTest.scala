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

package org.scalasteward.core.git

import munit.ScalaCheckSuite
import org.scalacheck.Prop.*
import org.scalasteward.core.TestInstances.*
import org.scalasteward.core.data.Update
import org.scalasteward.core.repoconfig.CommitsConfig
import org.scalasteward.core.update.show

class gitTest extends ScalaCheckSuite {
  test("commitMsgFor should work with static message") {
    val commitsConfig = CommitsConfig(Some("Static message"))
    forAll { (update: Update.Single) =>
      assertEquals(commitMsgFor(update, commitsConfig, None).title, "Static message")
    }
  }

  test("commitMsgFor adds branch if provided") {
    val commitsConfig = CommitsConfig(Some(s"$${default}"))
    val branch = Branch("some-branch")
    forAll { (update: Update.Single) =>
      val expected = s"Update ${show.oneLiner(update)} to ${update.nextVersion} in ${branch.name}"
      assertEquals(commitMsgFor(update, commitsConfig, Some(branch)).title, expected)
    }
  }

  test("commitMsgFor should work with default message") {
    val commitsConfig = CommitsConfig(Some(s"$${default}"))
    forAll { (update: Update.Single) =>
      val expected = s"Update ${show.oneLiner(update)} to ${update.nextVersion}"
      assertEquals(commitMsgFor(update, commitsConfig, None).title, expected)
    }
  }

  test("commitMsgFor should work with templated message") {
    val commitsConfig =
      CommitsConfig(Some(s"Update $${artifactName} from $${currentVersion} to $${nextVersion}"))
    forAll { (update: Update.Single) =>
      val expected =
        s"Update ${show.oneLiner(update)} from ${update.currentVersion} to ${update.nextVersion}"
      assertEquals(commitMsgFor(update, commitsConfig, None).title, expected)
    }
  }

  test("commitMsgFor should work with templated message and non-default branch") {
    val commitsConfig =
      CommitsConfig(
        Some(
          s"Update $${artifactName} from $${currentVersion} to $${nextVersion} in $${branchName}"
        )
      )
    val branch = Branch("some-branch")
    forAll { (update: Update.Single) =>
      val expected =
        s"Update ${show.oneLiner(update)} from ${update.currentVersion} to ${update.nextVersion} in ${branch.name}"
      assertEquals(commitMsgFor(update, commitsConfig, Some(branch)).title, expected)
    }
  }
}
