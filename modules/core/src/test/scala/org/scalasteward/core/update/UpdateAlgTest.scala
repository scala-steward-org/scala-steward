package org.scalasteward.core.update

import org.scalasteward.core.data.{Dependency, GroupId, Update}
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdateAlgTest extends AnyFunSuite with Matchers {
  test("findUpdateUnderNewGroup: returns empty if dep is not listed") {
    val original = Dependency(GroupId("org.spire-math"), "UNKNOWN", List("_2.12"), "1.0.0")
    UpdateAlg.findUpdateUnderNewGroup(original) shouldBe None
  }

  test("findUpdateUnderNewGroup: returns Update.Single for updating groupId") {
    val original = Dependency(GroupId("org.spire-math"), "kind-projector", List("_2.12"), "0.9.0")
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

  test("dependencyToUpdate") {
    val dependency = Dependency("groupId", "artifactId", List.empty, "1", Some(List("2")))
    UpdateAlg.dependencyToUpdate(dependency) shouldBe Some(
      Update.Single(GroupId("groupId"), "artifactId", "1", Nel.of("2"))
    )
  }
}
