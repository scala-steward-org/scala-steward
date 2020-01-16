package org.scalasteward.core.update

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.data.{ArtifactId, GroupId, Scope, Update}
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
    val actual = updateAlg
      .findUpdate(dependency.withMavenCentral, useCache = true)
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

  test("extractOutOfSyncDependencies") {
    val da1 = Scope("ga" % "a1" % "1.a", List.empty)
    val da2 = Scope("ga" % "a2" % "1.a", List.empty)
    val da3 = Scope("ga" % "a3" % "0.a", List.empty)
    val da4 = Scope("ga" % "a4" % "1.a", List.empty)
    val db1 = Scope("gb" % "b1" % "1.b", List.empty)
    val db2 = Scope("gb" % "b2" % "1.b", List.empty)
    val dc1 = Scope("gc" % "c1" % "1.c", List.empty)

    val ua1 = Single(da1.value, Nel.of("3.a"))
    val ua4 = Single(da4.value, Nel.of("2.a"))
    val ub1 = Single(db1.value, Nel.of("2.b"))
    val ub2 = Single(db2.value, Nel.of("2.b"))
    val uc1 = Single(dc1.value, Nel.of("2.c"))

    val dependencies = List(da1, da2, da3, da4, db1, db2, dc1)
    val updates = List(ua1, ua4, ub1, ub2, uc1)

    val (actualDependencies, actualUpdates) =
      UpdateAlg.extractOutOfSyncDependencies(dependencies, updates)
    actualDependencies shouldBe List(da1, da2, da4)
    actualUpdates shouldBe List(ub1, ub2, uc1)
  }
}
