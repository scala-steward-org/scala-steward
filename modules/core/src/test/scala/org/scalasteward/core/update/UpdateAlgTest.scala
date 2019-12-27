package org.scalasteward.core.update

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{ArtifactId, GroupId, Update}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdateAlgTest extends AnyFunSuite with Matchers {
  test("findUpdateWithNewerGroupId: returns empty if dep is not listed") {
    val original = "org.spire-math" % ArtifactId("UNKNOWN", "UNKNOWN_2.12") % "1.0.0"
    UpdateAlg.findUpdateWithNewerGroupId(original) shouldBe None
  }

  test("findUpdateWithNewerGroupId: returns Update.Single for updating groupId") {
    val original = "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.0"
    val expected = Update.Single(
      "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.0",
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel"))
    )
    UpdateAlg.findUpdateWithNewerGroupId(original) shouldBe Some(expected)
  }

  test("findUpdate: newer groupId") {
    val dependency = "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.10"
    val expected = Update.Single(
      "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.10",
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel"))
    )
    val actual = updateAlg.findUpdate(dependency).runA(MockState.empty).unsafeRunSync()
    actual shouldBe Some(expected)
  }
}
