package org.scalasteward.core.update

import better.files.File
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.application.{Cli, Config}
import org.scalasteward.core.data.{ArtifactId, GroupId, Update}
import org.scalasteward.core.mock.{MockContext, MockEff, MockState}
import org.scalasteward.core.update.ArtifactMigrations.ArtifactChange
import org.scalasteward.core.util.Nel
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import ArtifactMigrationsTest.TestMockContext

import scala.io.Source

class ArtifactMigrationsTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  override protected def afterAll(): Unit =
    ArtifactMigrationsTest.tempFile.delete()

  val standardGroupMigrations = List(
    ArtifactChange(
      groupIdBefore = Some(GroupId("org.spire-math")),
      groupIdAfter = GroupId("org.typelevel"),
      artifactIdBefore = None,
      artifactIdAfter = "kind-projector",
      initialVersion = "0.10.0"
    )
  )

  val standardArtifactMigrations = List(
    ArtifactChange(
      groupIdBefore = None,
      groupIdAfter = GroupId("com.nodifferent"),
      artifactIdBefore = Some("artifact-before"),
      artifactIdAfter = "artifact-after",
      initialVersion = "0.10.0"
    )
  )

  val standardBothMigrations = List(
    ArtifactChange(
      groupIdBefore = Some(GroupId("org.before")),
      groupIdAfter = GroupId("org.after"),
      artifactIdBefore = Some("artifact-before"),
      artifactIdAfter = "artifact-after",
      initialVersion = "0.10.0"
    )
  )

  test("migrateArtifact: for groupId, returns empty if artifactIdAfter is not listed") {
    val original = "org.spire-math" % ArtifactId("UNKNOWN", "UNKNOWN_2.12") % "1.0.0"
    ArtifactMigrations.migrateArtifact(original, standardGroupMigrations) shouldBe None
  }

  test("migrateArtifact: for artifactId, returns empty if groupIdAfter is not listed") {
    val original = "UNKNOWN" % ArtifactId("artifact-before", "artifact-before_2.12") % "1.0.0"
    ArtifactMigrations.migrateArtifact(original, standardArtifactMigrations) shouldBe None
  }

  test(
    "migrateArtifact: for both groupId and artifactId, returns empty if either of groupIdBefore or " +
      "artifactIdBefore are not listed"
  ) {
    val original1 = "UNKNOWN" % ArtifactId("artifact-before", "artifact-before_2.12") % "1.0.0"
    ArtifactMigrations.migrateArtifact(original1, standardBothMigrations) shouldBe None

    val original2 = "org.before" % ArtifactId("UNKNOWN", "UNKNOWN_2.12") % "1.0.0"
    ArtifactMigrations.migrateArtifact(original2, standardBothMigrations) shouldBe None
  }

  test("migrateArtifact: for groupId, returns Update.Single for updating groupId") {
    val original = "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.0"
    val expected = Update.Single(
      original,
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel")),
      Some("kind-projector")
    )
    ArtifactMigrations.migrateArtifact(original, standardGroupMigrations) shouldBe Some(expected)
  }

  test("migrateArtifact: for artifactId, returns Update.Single for updating artifactId") {
    val original =
      "com.nodifferent" % ArtifactId("artifact-before", "artifact-before_2.12") % "0.9.0"
    val expected = Update.Single(
      original,
      Nel.of("0.10.0"),
      Some(GroupId("com.nodifferent")),
      Some("artifact-after")
    )
    ArtifactMigrations.migrateArtifact(original, standardArtifactMigrations) shouldBe Some(expected)
  }

  test(
    "migrateArtifact: for both groupId and artifactId, returns Update.Single for updating both groupId " +
      "and artifactId"
  ) {
    val original = "org.before" % ArtifactId("artifact-before", "artifact-before_2.12") % "0.9.0"
    val expected = Update.Single(
      original,
      Nel.of("0.10.0"),
      Some(GroupId("org.after")),
      Some("artifact-after")
    )
    ArtifactMigrations.migrateArtifact(original, standardBothMigrations) shouldBe Some(expected)
  }

  test("findUpdate: newer groupId") {
    val dependency =
      "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.10"
    val expected = Update.Single(
      "org.spire-math" % ArtifactId("kind-projector", "kind-projector_2.12") % "0.9.10",
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel")),
      Some("kind-projector")
    )
    val actual = TestMockContext.artifactMigrations.findUpdateWithRenamedArtifact(dependency)
    actual shouldBe Some(expected)
  }

  test("findUpdate: newer artifactId") {
    val dependency =
      "org.typelevel" % ArtifactId("fake-artifact-id", "fake-artifact-id_2.12") % "0.9.10"
    val expected = Update.Single(
      "org.typelevel" % ArtifactId("fake-artifact-id", "fake-artifact-id_2.12") % "0.9.10",
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel")),
      Some("kind-projector")
    )
    val actual = TestMockContext.artifactMigrations.findUpdateWithRenamedArtifact(dependency)

    actual shouldBe Some(expected)
  }

  test("findUpdate: newer groupId and artifactId") {
    val dependency =
      "org.fake" % ArtifactId("fake-artifact-id", "fake-artifact-id_2.12") % "0.9.10"
    val expected = Update.Single(
      "org.fake" % ArtifactId("fake-artifact-id", "fake-artifact-id_2.12") % "0.9.10",
      Nel.of("0.10.0"),
      Some(GroupId("org.typelevel")),
      Some("kind-projector")
    )
    val actual = TestMockContext.artifactMigrations.findUpdateWithRenamedArtifact(dependency)

    actual shouldBe Some(expected)
  }
}

object ArtifactMigrationsTest {

  private val extraArtifactMigrationsString: String =
    Source.fromResource("extra-artifact-migrations.conf").mkString
  private val tempFile: File = File("temp")
  private val extraArtifactMigrationsFile: File = tempFile.overwrite(extraArtifactMigrationsString)

  object TestMockContext extends MockContext {
    implicit override val config: Config =
      Config.from(
        Cli.Args(
          workspace = File.temp / "test",
          reposFile = File.temp / "test",
          gitAuthorEmail = "test@example.org",
          vcsLogin = "test",
          gitAskPass = File.temp / "test",
          artifactMigrations = Some(extraArtifactMigrationsFile)
        )
      )
    implicit override val artifactMigrations: ArtifactMigrations =
      ArtifactMigrations
        .create[MockEff](config)
        .runA(
          MockState.empty.add(
            extraArtifactMigrationsFile,
            extraArtifactMigrationsString
          )
        )
        .unsafeRunSync()
  }
}
