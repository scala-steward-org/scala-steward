package org.scalasteward.core

import better.files.File
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.application.Config.{ArtifactCfg, RepoConfigCfg, ScalafixCfg}
import org.scalasteward.core.edit.scalafix.ScalafixMigrationsLoader
import org.scalasteward.core.mock.MockContext.context.{
  artifactMigrationsLoader,
  fileAlg,
  logger,
  scalafixMigrationsLoader
}
import org.scalasteward.core.mock.MockContext.mockState
import org.scalasteward.core.mock.MockEffOps
import org.scalasteward.core.repoconfig.RepoConfigLoader
import org.scalasteward.core.update.artifact.ArtifactMigrationsLoader
import scala.util.{Failure, Success, Try}

class BuiltinConfigFilesTest extends FunSuite {
  private def verifyConfigFile(readThis: String)(block: => Unit): Unit =
    Try {
      block
    } match {
      case Failure(e) =>
        fail(s"Invalid format. See ${readThis}", e)
      case Success(_) => // ok
    }

  test("migration v2 config is valid") {
    val migrationsContent =
      File("../src/main/resources/artifact-migrations.v2.conf").contentAsString
    val initialState =
      mockState.addUris(ArtifactMigrationsLoader.defaultArtifactMigrationsUrl -> migrationsContent)
    verifyConfigFile("See docs/artifact-migrations.md") {
      val migrations = artifactMigrationsLoader
        .loadAll(ArtifactCfg(List(), disableDefaults = false))
        .runA(initialState)
        .unsafeRunSync()
      assert(clue(migrations.size) > 1)
    }
  }

  test("scalafix migrations config is valid") {
    val migrationsContent =
      File("../src/main/resources/scalafix-migrations.conf").contentAsString
    val initialState =
      mockState.addUris(ScalafixMigrationsLoader.defaultScalafixMigrationsUrl -> migrationsContent)
    verifyConfigFile("docs/scalafix-migrations.md") {
      val migrations = scalafixMigrationsLoader
        .loadAll(ScalafixCfg(List(), disableDefaults = false))
        .runA(initialState)
        .attempt
        .unsafeRunSync()
        .getOrElse(List.empty)
      assert(clue(migrations).size > 1)
    }
  }

  test("default config is valid") {
    val defaultConfigContent =
      File("../src/main/resources/default.scala-steward.conf").contentAsString
    val initialState =
      mockState.addUris(RepoConfigLoader.defaultRepoConfigUrl -> defaultConfigContent)
    verifyConfigFile("docs/repo-specific-configuration.md") {
      val repoConfigLoader = new RepoConfigLoader
      val repoConfig = repoConfigLoader
        .loadGlobalRepoConfig(RepoConfigCfg(List(), disableDefault = false))
        .runA(initialState)
        .attempt
        .unsafeRunSync()
        .getOrElse(None)
      assert(clue(repoConfig).isDefined)
    }
  }
}
