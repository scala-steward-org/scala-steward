package org.scalasteward.core.update

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{ArtifactId, GroupId, Update}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.update.GroupMigrations.GroupIdChange
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GroupMigrationsTest extends AnyFunSuite with Matchers {

  val standardGroupMigrations = List(
    GroupIdChange(
      GroupId("org.spire-math"),
      GroupId("org.typelevel"),
      "kind-projector",
      "0.10.0"
    )
  )

  test("migrateGroupId: returns empty if dep is not listed") {
    val original = "org.spire-math" % ArtifactId("UNKNOWN", "UNKNOWN_2.12") % "1.0.0"
    GroupMigrations.migrateGroupId(original, standardGroupMigrations) shouldBe None
  }

  test("migrateGroupId: returns Update.Single for updating groupId") {
    val original = "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.0"
    val expected = Update.Single(
      original,
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel"))
    )
    GroupMigrations.migrateGroupId(original, standardGroupMigrations) shouldBe Some(expected)
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
}
