package org.scalasteward.core.update.artifact

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.buildtool.sbt.data.{SbtVersion, ScalaVersion}
import org.scalasteward.core.data.{GroupId, Resolver, Scope}
import org.scalasteward.core.mock.MockContext.context.updateAlg
import org.scalasteward.core.mock.{MockEffOps, MockState}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.update.UpdateAlg
import org.scalasteward.core.util.Nel

class ArtifactMigrationsFinderTest extends FunSuite {

  val standardGroupMigrations = new ArtifactMigrationsFinder(
    List(
      ArtifactChange(
        groupIdBefore = Some(GroupId("org.spire-math")),
        groupIdAfter = GroupId("org.typelevel"),
        artifactIdBefore = None,
        artifactIdAfter = "kind-projector"
      )
    )
  )

  val standardArtifactMigrations = new ArtifactMigrationsFinder(
    List(
      ArtifactChange(
        groupIdBefore = None,
        groupIdAfter = GroupId("com.nodifferent"),
        artifactIdBefore = Some("artifact-before"),
        artifactIdAfter = "artifact-after"
      )
    )
  )

  val standardBothMigrations = new ArtifactMigrationsFinder(
    List(
      ArtifactChange(
        groupIdBefore = Some(GroupId("org.before")),
        groupIdAfter = GroupId("org.after"),
        artifactIdBefore = Some("artifact-before"),
        artifactIdAfter = "artifact-after"
      )
    )
  )

  test("findArtifactChange: for groupId, returns empty if artifactIdAfter is not listed") {
    val original = "org.spire-math".g % ("UNKNOWN", "UNKNOWN_2.12").a % "1.0.0"
    val obtained = standardGroupMigrations.findArtifactChange(original)
    assertEquals(obtained, None)
  }

  test("findArtifactChange: for artifactId, returns empty if groupIdAfter is not listed") {
    val original = "UNKNOWN".g % ("artifact-before", "artifact-before_2.12").a % "1.0.0"
    val obtained = standardArtifactMigrations.findArtifactChange(original)
    assertEquals(obtained, None)
  }

  test(
    "findArtifactChange: for both groupId and artifactId, returns empty if either of groupIdBefore or " +
      "artifactIdBefore are not listed"
  ) {
    val original1 = "UNKNOWN".g % ("artifact-before", "artifact-before_2.12").a % "1.0.0"
    val obtained1 = standardBothMigrations.findArtifactChange(original1)
    assertEquals(obtained1, None)

    val original2 = "org.before".g % ("UNKNOWN", "UNKNOWN_2.12").a % "1.0.0"
    val obtained2 = standardBothMigrations.findArtifactChange(original2)
    assertEquals(obtained2, None)
  }

  test("findArtifactChange: for groupId, returns Update.Single for updating groupId") {
    val original = "org.spire-math".g % ("kind-projector", "kind-projector_2.12").a % "0.9.0"
    val obtained = standardGroupMigrations.findArtifactChange(original)
    val expected = ArtifactChange(
      Some("org.spire-math".g),
      "org.typelevel".g,
      None,
      "kind-projector"
    )
    assertEquals(obtained, Some(expected))
  }

  test("findArtifactChange: for artifactId, returns Update.Single for updating artifactId") {
    val original = "com.nodifferent".g % ("artifact-before", "artifact-before_2.12").a % "0.9.0"
    val obtained = standardArtifactMigrations.findArtifactChange(original)
    val expected = ArtifactChange(
      None,
      "com.nodifferent".g,
      Some("artifact-before"),
      "artifact-after"
    )
    assertEquals(obtained, Some(expected))
  }

  test(
    "findArtifactChange: for both groupId and artifactId, returns Update.Single for updating both groupId " +
      "and artifactId"
  ) {
    val original = "org.before".g % ("artifact-before", "artifact-before_2.12").a % "0.9.0"
    val obtained = standardBothMigrations.findArtifactChange(original)
    val expected = ArtifactChange(
      Some("org.before".g),
      "org.after".g,
      Some("artifact-before"),
      "artifact-after"
    )
    assertEquals(obtained, Some(expected))
  }

  test("findUpdates: newer groupId") {
    val dependency = "org.spire-math".g % ("kind-projector", "kind-projector_2.12").a % "0.9.10"
    val expected = ("org.spire-math".g % ("kind-projector", "kind-projector_2.12").a % "0.9.10" %>
      Nel.of("0.10.3")).single
      .copy(newerGroupId = Some("org.typelevel".g), newerArtifactId = Some("kind-projector"))
    val obtained = updateAlg
      .findUpdates(List(dependency.withMavenCentral), RepoConfig.empty, None)
      .runA(MockState.empty)
      .unsafeRunSync()
    assertEquals(obtained, List(expected))
  }

  test("migrateDependency: newer groupId") {
    val dependency = "org.spire-math".g % ("kind-projector", "kind-projector_2.12").a % "0.9.10"
    val artifactChange = ArtifactChange(
      Some("org.spire-math".g),
      "org.typelevel".g,
      Some("kind-projector"),
      "kind-projector"
    )
    val expected = "org.typelevel".g % ("kind-projector", "kind-projector_2.12").a % "0.9.10"
    val obtained = UpdateAlg.migrateDependency(dependency, artifactChange)
    assertEquals(obtained, expected)
  }

  test("migrateDependency: newer ArtifactId") {
    val dependency = "org.spire-math".g % ("kind-projector", "kind-projector_2.12").a % "0.9.10"
    val artifactChange = ArtifactChange(
      Some("org.spire-math".g),
      "org.spire-math".g,
      Some("kind-projector"),
      "new-projector"
    )
    val expected = "org.spire-math".g % ("new-projector", "new-projector_2.12").a % "0.9.10"
    val obtained = UpdateAlg.migrateDependency(dependency, artifactChange)
    assertEquals(obtained, expected)
  }

  test("migrateDependency: sbt-dynver with migration") {
    val dependency = ("com.dwijnand".g % "sbt-dynver".a % "4.1.1")
      .copy(sbtVersion = Some(SbtVersion("1.0")), scalaVersion = Some(ScalaVersion("2.12")))
    val expected = (dependency %> Nel.of("5.1.0")).single
      .copy(newerGroupId = Some("com.github.sbt".g), newerArtifactId = Some("sbt-dynver"))
    val obtained = updateAlg
      .findUpdates(
        List(Scope(dependency, List(sbtPluginReleases, Resolver.mavenCentral))),
        RepoConfig.empty,
        None
      )
      .runA(MockState.empty)
      .unsafeRunSync()

    assertEquals(obtained, List(expected))
  }
}
