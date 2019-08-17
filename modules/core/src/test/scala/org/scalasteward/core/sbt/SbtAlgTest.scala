package org.scalasteward.core.sbt

import better.files.File
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.Version
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockContext, MockState}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.{FunSuite, Matchers}

class SbtAlgTest extends FunSuite with Matchers {

  test("addGlobalPlugins") {
    sbtAlg.addGlobalPlugins.runS(MockState.empty).unsafeRunSync() shouldBe MockState.empty.copy(
      commands = Vector(
        List("write", "/tmp/steward/.sbt/0.13/plugins/scala-steward.sbt"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/scala-steward.sbt"),
        List("write", "/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala")
      ),
      logs = Vector((None, "Add global sbt plugins")),
      files = Map(
        File("/tmp/steward/.sbt/0.13/plugins/scala-steward.sbt") -> scalaStewardSbt.content,
        File("/tmp/steward/.sbt/1.0/plugins/scala-steward.sbt") -> scalaStewardSbt.content,
        File("/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala") -> stewardPlugin.content,
        File("/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala") -> stewardPlugin.content
      )
    )
  }

  test("getUpdatesForRepo") {
    val repo = Repo("fthomas", "refined")
    val repoDir = config.workspace / "fthomas/refined"
    val buildProperties = repoDir / "project" / "build.properties"
    val initialState = MockState.empty.copy(files = Map(buildProperties -> "sbt.version=1.2.6"))
    val state = sbtAlg.getUpdatesForRepo(repo).runS(initialState).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      files = Map(buildProperties -> "sbt.version=1.2.6"),
      commands = Vector(
        List("read", s"$repoDir/project/build.properties"),
        List("create", s"$repoDir/project/tmp-sbt-dep.sbt"),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          ";set every credentials := Nil;dependencyUpdates;reload plugins;dependencyUpdates"
        ),
        List("rm", s"$repoDir/project/tmp-sbt-dep.sbt")
      )
    )
  }

  test("getUpdatesForRepo ignoring .jvmopts and .sbtopts files") {
    implicit val config: Config = MockContext.config.copy(ignoreOptsFiles = true)
    val sbtAlgKeepingCredentials = SbtAlg.create
    val repo = Repo("fthomas", "refined")
    val repoDir = config.workspace / "fthomas/refined"
    val state =
      sbtAlgKeepingCredentials.getUpdatesForRepo(repo).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", s"$repoDir/project/build.properties"),
        List("rm", (repoDir / ".jvmopts").toString),
        List("rm", (repoDir / ".sbtopts").toString),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          ";set every credentials := Nil;dependencyUpdates;reload plugins;dependencyUpdates"
        ),
        List("restore", (repoDir / ".sbtopts").toString),
        List("restore", (repoDir / ".jvmopts").toString)
      )
    )
  }

  test("getUpdatesForRepo keeping credentials") {
    implicit val config: Config = MockContext.config.copy(keepCredentials = true)
    val sbtAlgKeepingCredentials = SbtAlg.create
    val repo = Repo("fthomas", "refined")
    val repoDir = config.workspace / "fthomas/refined"
    val state =
      sbtAlgKeepingCredentials.getUpdatesForRepo(repo).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", s"$repoDir/project/build.properties"),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          ";dependencyUpdates;reload plugins;dependencyUpdates"
        )
      )
    )
  }

  test("runMigrations") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.owner / repo.repo
    val migrations = Nel.of(
      Migration(
        "co.fs2",
        Nel.of("fs2-core".r),
        Version("1.0.0"),
        Nel.of("github:functional-streams-for-scala/fs2/v1?sha=v1.0.5")
      )
    )
    val state = sbtAlg.runMigrations(repo, migrations).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("create", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt"),
        List("create", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          ";scalafixEnable;scalafix github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"
        ),
        List("rm", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List("rm", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt")
      )
    )
  }

  test("runMigrations for test codes") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.owner / repo.repo
    val migrations = Nel.of(
      Migration(
        "org.scalatest",
        Nel.of("scalatest".r),
        Version("3.1.0"),
        Nel.of(
          "https://raw.githubusercontent.com/scalatest/autofix/6168da0e2bd113872b7dcd22cad7688d97ef9381/3.0.x/rules/src/main/scala/org/scalatest/autofix/v3_0_x/RenameDeprecatedPackage.scala",
          "https://raw.githubusercontent.com/scalatest/autofix/6168da0e2bd113872b7dcd22cad7688d97ef9381/3.1.x/rules/src/main/scala/org/scalatest/autofix/v3_1_x/RewriteDeprecatedNames.scala"
        ),
        Some("test")
      )
    )
    val state = sbtAlg.runMigrations(repo, migrations).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("create", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt"),
        List("create", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          ";scalafixEnable;test:scalafix https://raw.githubusercontent.com/scalatest/autofix/6168da0e2bd113872b7dcd22cad7688d97ef9381/3.0.x/rules/src/main/scala/org/scalatest/autofix/v3_0_x/RenameDeprecatedPackage.scala;test:scalafix https://raw.githubusercontent.com/scalatest/autofix/6168da0e2bd113872b7dcd22cad7688d97ef9381/3.1.x/rules/src/main/scala/org/scalatest/autofix/v3_1_x/RewriteDeprecatedNames.scala"
        ),
        List("rm", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List("rm", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt")
      )
    )
  }
}
