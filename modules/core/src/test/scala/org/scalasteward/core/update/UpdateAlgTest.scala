package org.scalasteward.core.update

import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Update}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdateAlgTest extends AnyFunSuite with Matchers {
  test("findUpdateWithNewerGroupId: returns empty if dep is not listed") {
    val original =
      Dependency(GroupId("org.spire-math"), ArtifactId("UNKNOWN", "UNKNOWN_2.12"), "1.0.0")
    UpdateAlg.findUpdateWithNewerGroupId(original) shouldBe None
  }

  test("findUpdateWithNewerGroupId: returns Update.Single for updating groupId") {
    val original = Dependency(
      GroupId("org.spire-math"),
      ArtifactId("kind-projector", "kind-projector_2.12"),
      "0.9.0"
    )
    val expected = Update.Single(
      GroupId("org.spire-math"),
      ArtifactId("kind-projector", "kind-projector_2.12"),
      "0.9.0",
      Nel.of("0.10.0"),
      newerGroupId = Some(GroupId("org.typelevel"))
    )
    UpdateAlg.findUpdateWithNewerGroupId(original) shouldBe Some(expected)
  }

  test("findUpdate: newer groupId") {
    val dependency = Dependency(
      GroupId("org.spire-math"),
      ArtifactId("kind-projector", "kind-projector_2.12"),
      "0.9.10"
    )
    val expected = Update.Single(
      GroupId("org.spire-math"),
      ArtifactId("kind-projector", "kind-projector_2.12"),
      "0.9.10",
      Nel.of("0.10.0"),
      newerGroupId = Some(GroupId("org.typelevel"))
    )
    val actual = updateAlg.findUpdate(dependency).runA(MockState.empty).unsafeRunSync()
    actual shouldBe Some(expected)
  }

  test("findUpdates: ignored dependency") {
    val dependency = Dependency(
      GroupId("org.scala-lang"),
      ArtifactId("scala-library"),
      "2.12.8"
    )
    val actual = updateAlg
      .findUpdates(List(dependency), RepoConfig.default)
      .runA(MockState.empty)
      .unsafeRunSync()
    actual shouldBe List()
  }
}
