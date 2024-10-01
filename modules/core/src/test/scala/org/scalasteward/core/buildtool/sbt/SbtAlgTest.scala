package org.scalasteward.core.buildtool.sbt

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.data.{GroupId, Repo, Version}
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.util.Nel

class SbtAlgTest extends FunSuite {
  private val workspace = workspaceAlg.rootDir.unsafeRunSync()

  test("getDependencies") {
    val repo = Repo("sbt-alg", "test-1")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val initial = MockState.empty
      .addFiles(repoDir / "project" / "build.properties" -> "sbt.version=1.3.11")
      .unsafeRunSync()
    val state = sbtAlg.getDependencies(buildRoot).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Cmd("read", s"$repoDir/project/build.properties"),
        Cmd("test", "-d", s"$repoDir/project"),
        Cmd("test", "-d", s"$repoDir/project/project"),
        Cmd("read", "classpath:StewardPlugin_1_3_11.scala"),
        Cmd("write", s"$repoDir/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd("write", s"$repoDir/project/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd.execSandboxed(
          repoDir,
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          "-Dsbt.server.forcestart=true",
          s";$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        ),
        Cmd("rm", "-rf", s"$repoDir/project/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd("rm", "-rf", s"$repoDir/project/scala-steward-StewardPlugin_1_3_11.scala")
      )
    )
    assertEquals(state, expected)
  }

  test("runMigrations") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val migration = ScalafixMigration(
      GroupId("co.fs2"),
      Nel.of("fs2-core"),
      Version("1.0.0"),
      Nel.of("github:functional-streams-for-scala/fs2/v1?sha=v1.0.5")
    )
    val initialState = MockState.empty
      .addFiles(
        workspace / s"store/versions/v2/https/repo1.maven.org/maven2/ch/epfl/scala/sbt-scalafix_2.12_1.0/versions.json" -> sbtScalafixVersionJson
      )
      .unsafeRunSync()
    val state = sbtAlg.runMigration(buildRoot, migration).runS(initialState).unsafeRunSync()
    val expected = initialState.copy(
      trace = Vector(
        Cmd(
          "read",
          s"$workspace/store/versions/v2/https/repo1.maven.org/maven2/ch/epfl/scala/sbt-scalafix_2.12_1.0/versions.json"
        ),
        Cmd("write", s"$repoDir/project/scala-steward-sbt-scalafix.sbt"),
        Cmd.execSandboxed(
          repoDir,
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          "-Dsbt.server.forcestart=true",
          s";$scalafixEnable;$scalafixAll github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"
        ),
        Cmd("rm", "-rf", s"$repoDir/project/scala-steward-sbt-scalafix.sbt")
      )
    )
    assertEquals(state, expected)
  }

  test("runMigrations: migration with scalacOptions") {
    val repo = Repo("fthomas", "scala-steward")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val migration = ScalafixMigration(
      GroupId("org.typelevel"),
      Nel.of("cats-core"),
      Version("2.2.0"),
      Nel.of("github:cb372/cats/Cats_v2_2_0?sha=235bd7c92e431ab1902db174cf4665b05e08f2f1"),
      scalacOptions = Some(Nel.of("-P:semanticdb:synthetics:on"))
    )
    val initialState = MockState.empty
      .addFiles(
        workspace / s"store/versions/v2/https/repo1.maven.org/maven2/ch/epfl/scala/sbt-scalafix_2.12_1.0/versions.json" -> sbtScalafixVersionJson
      )
      .unsafeRunSync()
    val state = sbtAlg.runMigration(buildRoot, migration).runS(initialState).unsafeRunSync()
    val expected = initialState.copy(
      trace = Vector(
        Cmd(
          "read",
          s"$workspace/store/versions/v2/https/repo1.maven.org/maven2/ch/epfl/scala/sbt-scalafix_2.12_1.0/versions.json"
        ),
        Cmd("write", s"$repoDir/project/scala-steward-sbt-scalafix.sbt"),
        Cmd("write", s"$repoDir/scala-steward-scalafix-options.sbt"),
        Cmd.execSandboxed(
          repoDir,
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          "-Dsbt.server.forcestart=true",
          s";$scalafixEnable;$scalafixAll github:cb372/cats/Cats_v2_2_0?sha=235bd7c92e431ab1902db174cf4665b05e08f2f1"
        ),
        Cmd("rm", "-rf", s"$repoDir/scala-steward-scalafix-options.sbt"),
        Cmd("rm", "-rf", s"$repoDir/project/scala-steward-sbt-scalafix.sbt")
      )
    )
    assertEquals(state, expected)
  }

  private def sbtScalafixVersionJson =
    s"""|{
        |  "updatedAt" : 9999999999999,
        |  "versions" : [
        |    "0.9.33",
        |    "0.9.34"
        |  ]
        |}""".stripMargin
}
