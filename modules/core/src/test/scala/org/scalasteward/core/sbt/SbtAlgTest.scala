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

  test("getUpdatesForRepo") {
    val repo = Repo("fthomas", "refined")
    val repoDir = config.workspace / "fthomas/refined"
    val files = Map(
      repoDir / "project" / "build.properties" -> "sbt.version=1.2.6",
      repoDir / ".scalafmt.conf" -> "version=2.0.0"
    )

    files.foreach {
      case (file, content) =>
        val initialState = MockState.empty.copy(files = Map(file -> content))
        val state = sbtAlg.getUpdatesForRepo(repo).runS(initialState).unsafeRunSync()
        state shouldBe MockState.empty.copy(
          files = Map(file -> content),
          commands = Vector(
            List("read", s"$repoDir/project/build.properties"),
            List("read", s"$repoDir/.scalafmt.conf"),
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
              s";$setDependencyUpdatesFailBuild;$dependencyUpdates;$reloadPlugins;$dependencyUpdates"
            ),
            List("rm", s"$repoDir/project/tmp-sbt-dep.sbt"),
            List("read", s"${config.workspace}/repos_v6.json")
          )
        )
    }
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
        List("read", s"$repoDir/.scalafmt.conf"),
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
          s";$setDependencyUpdatesFailBuild;$dependencyUpdates;$reloadPlugins;$dependencyUpdates"
        ),
        List("restore", (repoDir / ".sbtopts").toString),
        List("restore", (repoDir / ".jvmopts").toString),
        List("read", s"${config.workspace}/repos_v6.json")
      )
    )
  }

  test("runMigrations") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.owner / repo.repo
    val migrations = Nel.of(
      Migration(
        GroupId("co.fs2"),
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
          ";++2.12.10!;scalafixEnable;scalafix github:functional-streams-for-scala/fs2/v1?sha=v1.0.5;test:scalafix github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"
        ),
        List("rm", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List("rm", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt")
      )
    )
  }
}
