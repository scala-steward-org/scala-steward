package org.scalasteward.core.update

import org.scalasteward.core.data.{Dependency, GroupId, Update}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdateAlgTest extends AnyFunSuite with Matchers {
  test("findUpdateUnderNewGroup: returns empty if dep is not listed") {
    val original = Dependency(GroupId("org.spire-math"), "UNKNOWN", "_2.12", "1.0.0")
    UpdateAlg.findUpdateUnderNewGroup(original) shouldBe None
  }

  test("findUpdateUnderNewGroup: returns Update.Single for updateing groupId") {
    val original = Dependency(GroupId("org.spire-math"), "kind-projector", "_2.12", "0.9.0")
    UpdateAlg.findUpdateUnderNewGroup(original) shouldBe Some(
      Update.Single(
        GroupId("org.spire-math"),
        "kind-projector",
        "0.9.0",
        Nel.of("0.10.0"),
        newerGroupId = Some(GroupId("org.typelevel"))
      )
    )
  }

  test("findUpdate: newer groupId") {
    val dependency =
      Dependency(GroupId("org.spire-math"), "kind-projector", "kind-projector_2.12", "0.9.10")
    val expected = Update.Single(
      GroupId("org.spire-math"),
      "kind-projector",
      "0.9.10",
      Nel.of("0.10.0"),
      newerGroupId = Some(GroupId("org.typelevel"))
    )

    val actual = updateAlg.findUpdate(dependency).runA(MockState.empty).unsafeRunSync()
    actual shouldBe Some(expected)
  }
}
