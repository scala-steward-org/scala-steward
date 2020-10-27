package org.scalasteward.core.update

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{ArtifactId, GroupId, Resolver, Scope, Update}
import org.scalasteward.core.mock.MockContext.updateAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UpdateAlgTest extends AnyFunSuite with Matchers {

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

  test("findUpdates") {
    val dependency = "io.circe" % ArtifactId("circe-refined", "circe-refined_2.12") % "0.11.2"
    val updates: List[Update.Single] = updateAlg
      .findUpdates(
        dependencies = List(Scope(value = dependency, resolvers = List(Resolver.mavenCentral))),
        repoConfig = RepoConfig.empty,
        maxAge = None
      )
      .runA(
        MockState.empty
      )
      .unsafeRunSync()
    val newerVersions = updates.headOption
      .map(_.newerVersions)
      .getOrElse(Nel.one(""))
    val update: Update.Single =
      Update.Single(crossDependency = dependency, newerVersions = newerVersions)

    updates.contains(update) shouldBe true
  }

  test("findUpdates returns additional artifact migration artifacts") {
    val dependency = "io.circe" % ArtifactId("circe-refined", "circe-refined_2.12") % "0.11.2"
    val artMigrationVersion = "0.12.0"
    val updates: List[Update.Single] = ArtifactMigrationsTest.TestMockContext.updateAlg
      .findUpdates(
        dependencies = List(Scope(value = dependency, resolvers = List(Resolver.mavenCentral))),
        repoConfig = RepoConfig.empty,
        maxAge = None
      )
      .runA(MockState.empty)
      .unsafeRunSync()
    val newerVersions = updates.headOption
      .map(_.newerVersions)
      .getOrElse(Nel.one(""))
    val update: Update.Single =
      Update.Single(crossDependency = dependency, newerVersions = newerVersions)
    val artMigrationUpdated: Update.Single = Update.Single(
      crossDependency = dependency,
      newerVersions = Nel.one(artMigrationVersion),
      newerGroupId = Some(GroupId("io.circe")),
      newerArtifactId = Some("different-circe-refined")
    )

    updates.contains(update) shouldBe true
    updates.contains(artMigrationUpdated) shouldBe true
  }
}
