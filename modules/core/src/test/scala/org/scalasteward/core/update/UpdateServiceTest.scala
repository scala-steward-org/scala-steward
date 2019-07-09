package org.scalasteward.core.update

import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class UpdateServiceTest extends FunSuite with Matchers {

  test("findUpdateUnderNewGroup: returns empty if dep is not listed") {
    val original = new Dependency("org.spire-math", "UNKNOWN", "_2.12", "1.0.0")
    UpdateService.findUpdateUnderNewGroup(original) shouldBe None
  }

  test("findUpdateUnderNewGroup: returns Update.Single for updateing groupId") {
    val original = new Dependency("org.spire-math", "kind-projector", "_2.12", "0.9.0")
    UpdateService.findUpdateUnderNewGroup(original) shouldBe Some(
      Update.Single(
        "org.spire-math",
        "kind-projector",
        "0.9.0",
        Nel.of("0.10.0"),
        newerGroupId = Some("org.typelevel")
      )
    )
  }

}
