package org.scalasteward.core.sbt

import better.files.File
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.{MockContext, MockState}
import org.scalasteward.core.sbt.command._
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SbtAlgTest extends AnyFunSuite with Matchers {
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

  test("getDependencies") {
    val repo = Repo("typelevel", "cats")
    val repoDir = config.workspace / repo.show
    val files = Map(
      repoDir / "project" / "build.properties" -> "sbt.version=1.2.6",
      repoDir / ".scalafmt.conf" -> "version=2.0.0"
    )
    val state =
      sbtAlg.getDependencies(repo).runS(MockState.empty.copy(files = files)).unsafeRunSync()
    state shouldBe MockState.empty.copy(
      commands = Vector(
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          s";$stewardDependencies;$reloadPlugins;$stewardDependencies"
        ),
        List("read", s"$repoDir/project/build.properties"),
        List("read", s"$repoDir/.scalafmt.conf")
      ),
      files = files
    )
  }

  test("getUpdatesForRepo") {
    val repo = Repo("fthomas", "refined")
    val repoDir = config.workspace / "fthomas/refined"
    val files = Map(
      repoDir / "project" / "build.properties" -> "sbt.version=1.2.6",
      repoDir / ".scalafmt.conf" -> "version=2.0.0"
    )
    val initialState = MockState.empty.copy(files = files)
    val state = sbtAlg.getUpdatesForRepo(repo).runS(initialState).unsafeRunSync()
    state shouldBe initialState.copy(
      commands = Vector(
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          s";$stewardDependencies;$stewardUpdates;$reloadPlugins;$stewardDependencies;$stewardUpdates"
        ),
        List("read", s"$repoDir/project/build.properties"),
        List("read", s"$repoDir/.scalafmt.conf")
      ),
      logs = Vector(
        (None, "Found update: org.scala-sbt:sbt : 1.2.6 -> 1.2.8"),
        (None, "Found update: org.scalameta:scalafmt-core : 2.0.0 -> 2.0.1")
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
          s";$stewardDependencies;$stewardUpdates;$reloadPlugins;$stewardDependencies;$stewardUpdates"
        ),
        List("restore", (repoDir / ".sbtopts").toString),
        List("restore", (repoDir / ".jvmopts").toString),
        List("read", s"$repoDir/project/build.properties"),
        List("read", s"$repoDir/.scalafmt.conf")
      )
    )
  }

  test("runMigrations") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.owner / repo.repo
    val migrations = Nel.of(
      Migration(
        GroupId("co.fs2"),
        Nel.of("fs2-core"),
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
          ";scalafixEnable;scalafix github:functional-streams-for-scala/fs2/v1?sha=v1.0.5;test:scalafix github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"
        ),
        List("rm", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List("rm", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt")
      )
    )
  }
}
