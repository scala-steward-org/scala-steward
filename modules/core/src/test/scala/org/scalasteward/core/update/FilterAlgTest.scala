package org.scalasteward.core.update

import org.scalasteward.core.model.Update
import org.scalatest.{FunSuite, Matchers}
import org.scalasteward.core.util.Nel

class FilterAlgTest extends FunSuite with Matchers {
  test("removeBadVersions: update without bad version") {
    val update = Update.Single("com.jsuereth", "sbt-pgp", "1.1.0", Nel.of("1.1.2", "2.0.0"))
    FilterAlg.removeBadVersions(update) shouldBe Some(update)
  }

  test("removeBadVersions: update with bad version") {
    val update = Update.Single("com.jsuereth", "sbt-pgp", "1.1.2-1", Nel.of("1.1.2", "2.0.0"))
    FilterAlg.removeBadVersions(update) shouldBe Some(update.copy(newerVersions = Nel.of("2.0.0")))
  }
}
