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
    val dependency =
      "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.10"
    val expected = Update.Single(
      "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.10",
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel"))
    )
    val actual = updateAlg
      .findUpdate(dependency.withMavenCentral, None)
      .runA(MockState.empty)
      .unsafeRunSync()
    actual shouldBe Some(expected)
  }

  test("isUpdateFor") {
    val dependency = "io.circe" % ArtifactId("circe-refined", "circe-refined_2.12") % "0.11.2"
    val update = Update.Group(
      Nel.of(
        "io.circe" % ArtifactId("circe-core", "circe-core_2.12") % "0.11.2",
        "io.circe" % Nel.of(
          ArtifactId("circe-refined", "circe-refined_2.12"),
          ArtifactId("circe-refined", "circe-refined_sjs0.6_2.12")
        ) % "0.11.2"
      ),
      Nel.of("0.12.3")
    )
    UpdateAlg.isUpdateFor(update, dependency) shouldBe true
  }
}
