package org.scalasteward.core.buildtool.sbt

import cats.data.Kleisli
import munit.FunSuite
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.mock.MockContext.context.sbtAlg
import org.scalasteward.core.mock.MockContext.{config, mockRoot}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

class SbtAlgTest extends FunSuite {
  test("addGlobalPlugins") {
    val obtained = sbtAlg
      .addGlobalPlugins(Kleisli(_.update(_.exec(List("fa")))))
      .runS(MockState.empty)
      .unsafeRunSync()
    val expected = MockState.empty.copy(
      trace = Vector(
        Log("Add global sbt plugins"),
        Cmd("read", "classpath:org/scalasteward/sbt/plugin/StewardPlugin.scala"),
        Cmd("write", s"$mockRoot/.sbt/0.13/plugins/StewardPlugin.scala"),
        Cmd("write", s"$mockRoot/.sbt/1.0/plugins/StewardPlugin.scala"),
        Cmd("fa"),
        Cmd("rm", "-rf", s"$mockRoot/.sbt/1.0/plugins/StewardPlugin.scala"),
        Cmd("rm", "-rf", s"$mockRoot/.sbt/0.13/plugins/StewardPlugin.scala")
      )
    )
    assertEquals(obtained, expected)
  }

  test("getDependencies") {
    val repo = Repo("typelevel", "cats")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.show
    val files = Map(repoDir / "project" / "build.properties" -> "sbt.version=1.2.6")
    val initial = MockState.empty.copy(files = files)
    val state = sbtAlg.getDependencies(buildRoot).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Cmd(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          s";$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        ),
        Cmd("read", s"$repoDir/project/build.properties")
      )
    )
    assertEquals(state, expected)
  }

  test("runMigrations") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.show
    val migration = ScalafixMigration(
      GroupId("co.fs2"),
      Nel.of("fs2-core"),
      Version("1.0.0"),
      Nel.of("github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"),
      None,
      None
    )
    val state = sbtAlg.runMigration(buildRoot, migration).runS(MockState.empty).unsafeRunSync()
    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("write", s"$mockRoot/.sbt/0.13/plugins/scala-steward-scalafix.sbt"),
        Cmd("write", s"$mockRoot/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        Cmd(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          s";$scalafixEnable;$scalafixAll github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"
        ),
        Cmd("rm", "-rf", s"$mockRoot/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        Cmd("rm", "-rf", s"$mockRoot/.sbt/0.13/plugins/scala-steward-scalafix.sbt")
      )
    )
    assertEquals(state, expected)
  }

  test("runMigrations: migration with scalacOptions") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = config.workspace / repo.show
    val migration = ScalafixMigration(
      GroupId("org.typelevel"),
      Nel.of("cats-core"),
      Version("2.2.0"),
      Nel.of("github:cb372/cats/Cats_v2_2_0?sha=235bd7c92e431ab1902db174cf4665b05e08f2f1"),
      None,
      Some(Nel.of("-P:semanticdb:synthetics:on"))
    )
    val state = sbtAlg.runMigration(buildRoot, migration).runS(MockState.empty).unsafeRunSync()
    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd("write", s"$mockRoot/.sbt/0.13/plugins/scala-steward-scalafix.sbt"),
        Cmd("write", s"$mockRoot/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        Cmd("write", s"$repoDir/scala-steward-scalafix-options.sbt"),
        Cmd(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          s";$scalafixEnable;$scalafixAll github:cb372/cats/Cats_v2_2_0?sha=235bd7c92e431ab1902db174cf4665b05e08f2f1"
        ),
        Cmd("rm", "-rf", s"$repoDir/scala-steward-scalafix-options.sbt"),
        Cmd("rm", "-rf", s"$mockRoot/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        Cmd("rm", "-rf", s"$mockRoot/.sbt/0.13/plugins/scala-steward-scalafix.sbt")
      )
    )
    assertEquals(state, expected)
  }
}
