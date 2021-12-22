package org.scalasteward.core.update.artifact

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.GroupId
import org.scalasteward.core.mock.MockContext.context.updateAlg
import org.scalasteward.core.mock.MockState

class ArtifactMigrationsFinderTest extends FunSuite {

  val standardGroupMigrations = new ArtifactMigrationsFinder(
    List(
      ArtifactChange(
        groupIdBefore = Some(GroupId("org.spire-math")),
        groupIdAfter = GroupId("org.typelevel"),
        artifactIdBefore = None,
        artifactIdAfter = "kind-projector",
        initialVersion = "0.10.0"
      )
    )
  )

  val standardArtifactMigrations = new ArtifactMigrationsFinder(
    List(
      ArtifactChange(
        groupIdBefore = None,
        groupIdAfter = GroupId("com.nodifferent"),
        artifactIdBefore = Some("artifact-before"),
        artifactIdAfter = "artifact-after",
        initialVersion = "0.10.0"
      )
    )
  )

  val standardBothMigrations = new ArtifactMigrationsFinder(
    List(
      ArtifactChange(
        groupIdBefore = Some(GroupId("org.before")),
        groupIdAfter = GroupId("org.after"),
        artifactIdBefore = Some("artifact-before"),
        artifactIdAfter = "artifact-after",
        initialVersion = "0.10.0"
      )
    )
  )

  test(
    "findUpdateWithRenamedArtifact: for groupId, returns empty if artifactIdAfter is not listed"
  ) {
    val original = "org.spire-math".g % ("UNKNOWN", "UNKNOWN_2.12").a % "1.0.0"
    val obtained = standardGroupMigrations.findUpdateWithRenamedArtifact(original)
    assertEquals(obtained, None)
  }

  test(
    "findUpdateWithRenamedArtifact: for artifactId, returns empty if groupIdAfter is not listed"
  ) {
    val original = "UNKNOWN".g % ("artifact-before", "artifact-before_2.12").a % "1.0.0"
    val obtained = standardArtifactMigrations.findUpdateWithRenamedArtifact(original)
    assertEquals(obtained, None)
  }

  test(
    "findUpdateWithRenamedArtifact: for both groupId and artifactId, returns empty if either of groupIdBefore or " +
      "artifactIdBefore are not listed"
  ) {
    val original1 = "UNKNOWN".g % ("artifact-before", "artifact-before_2.12").a % "1.0.0"
    val obtained1 = standardBothMigrations.findUpdateWithRenamedArtifact(original1)
    assertEquals(obtained1, None)

    val original2 = "org.before".g % ("UNKNOWN", "UNKNOWN_2.12").a % "1.0.0"
    val obtained2 = standardBothMigrations.findUpdateWithRenamedArtifact(original2)
    assertEquals(obtained2, None)
  }

  test("findUpdateWithRenamedArtifact: for groupId, returns Update.Single for updating groupId") {
    val original = "org.spire-math".g % ("kind-projector", "kind-projector_2.12").a % "0.9.0"
    val obtained = standardGroupMigrations.findUpdateWithRenamedArtifact(original)
    val expected = (original %> "0.10.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("kind-projector"))
    assertEquals(obtained, Some(expected))
  }

  test(
    "findUpdateWithRenamedArtifact: for artifactId, returns Update.Single for updating artifactId"
  ) {
    val original = "com.nodifferent".g % ("artifact-before", "artifact-before_2.12").a % "0.9.0"
    val obtained = standardArtifactMigrations.findUpdateWithRenamedArtifact(original)
    val expected = (original %> "0.10.0").single
      .copy(newerGroupId = Some("com.nodifferent".g), newerArtifactId = Some("artifact-after"))
    assertEquals(obtained, Some(expected))
  }

  test(
    "findUpdateWithRenamedArtifact: for both groupId and artifactId, returns Update.Single for updating both groupId " +
      "and artifactId"
  ) {
    val original = "org.before".g % ("artifact-before", "artifact-before_2.12").a % "0.9.0"
    val obtained = standardBothMigrations.findUpdateWithRenamedArtifact(original)
    val expected = (original %> "0.10.0").single
      .copy(newerGroupId = Some("org.after".g), newerArtifactId = Some("artifact-after"))
    assertEquals(obtained, Some(expected))
  }

  test("findUpdate: newer groupId") {
    val dependency = "org.spire-math".g % ("kind-projector", "kind-projector_2.12").a % "0.9.10"
    val expected = ("org.spire-math".g % ("kind-projector", "kind-projector_2.12").a % "0.9.10" %>
      "0.10.0").single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("kind-projector"))
    val obtained = updateAlg
      .findUpdate(dependency.withMavenCentral, None)
      .runA(MockState.empty)
      .unsafeRunSync()
    assertEquals(obtained, Some(expected))
  }
}
