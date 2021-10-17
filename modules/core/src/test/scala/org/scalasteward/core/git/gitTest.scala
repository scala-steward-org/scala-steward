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
    val branch = Branch("some-branch")
    forAll { update: Update =>
      assertEquals(commitMsgFor(update, commitsConfig, branch), "Static message")
    }
  }

  test("commitMsgFor should work with default message") {
    val commitsConfig = CommitsConfig(Some(s"$${default}"))
    val branch = Branch("some-branch")
    forAll { update: Update =>
      val expected = s"Update ${show.oneLiner(update)} to ${update.nextVersion} in ${branch.name}"
      assertEquals(commitMsgFor(update, commitsConfig, branch), expected)
    }
  }

  test("commitMsgFor should work with templated message") {
    val commitsConfig =
      CommitsConfig(
        Some(
          s"Update $${artifactName} from $${currentVersion} to $${nextVersion} in $${branchName}"
        )
      )
    val branch = Branch("some-branch")
    forAll { update: Update =>
      val expected =
        s"Update ${show.oneLiner(update)} from ${update.currentVersion} to ${update.nextVersion} in ${branch.name}"
      assertEquals(commitMsgFor(update, commitsConfig, branch), expected)
    }
  }

}
