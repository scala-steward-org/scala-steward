package org.scalasteward.core.git

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalasteward.core.TestSyntax._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.data.Update
import org.scalasteward.core.util.Nel
import org.scalasteward.core.update.show
import org.scalasteward.core.repoconfig.CommitsConfig

class gitTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {
  implicit val updateArbitrary: Arbitrary[Update] = Arbitrary(for {
    groupId <- Gen.alphaStr
    artifactId <- Gen.alphaStr
    currentVersion <- Gen.alphaStr
    newerVersion <- Gen.alphaStr
  } yield Single(groupId % artifactId % currentVersion, Nel.one(newerVersion)))

  test("commitMsgFor should work with static message") {
    val commitsConfig = CommitsConfig("Static message")
    forAll { update: Update =>
      commitMsgFor(update, commitsConfig) shouldBe "Static message"
    }
  }

  test("commitMsgFor should work with templated message") {
    val commitsConfig = CommitsConfig("Update ${artifactName} from ${currentVersion} to ${nextVersion}")
    forAll { update: Update =>
      commitMsgFor(update, commitsConfig) shouldBe s"Update ${show.oneLiner(update)} from ${update.currentVersion} to ${update.nextVersion}"
    }
  }
    
}