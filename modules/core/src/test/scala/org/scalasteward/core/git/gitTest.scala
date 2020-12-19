package org.scalasteward.core.git

import munit.ScalaCheckSuite
import org.scalacheck.Prop._
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.data.Update
import org.scalasteward.core.repoconfig.CommitsConfig
import org.scalasteward.core.update.show

class gitTest extends ScalaCheckSuite {
  test("commitMsgFor should work with static message") {
    val commitsConfig = CommitsConfig(Some("Static message"))
    forAll { update: Update => assertEquals(commitMsgFor(update, commitsConfig), "Static message") }
  }

  test("commitMsgFor should work with default message") {
    val commitsConfig = CommitsConfig(Some("${default}"))
    forAll { update: Update =>
      val expected = s"Update ${show.oneLiner(update)} to ${update.nextVersion}"
      assertEquals(commitMsgFor(update, commitsConfig), expected)
    }
  }

  test("commitMsgFor should work with templated message") {
    val commitsConfig =
      CommitsConfig(Some("Update ${artifactName} from ${currentVersion} to ${nextVersion}"))
    forAll { update: Update =>
      val expected =
        s"Update ${show.oneLiner(update)} from ${update.currentVersion} to ${update.nextVersion}"
      assertEquals(commitMsgFor(update, commitsConfig), expected)
    }
  }
}
