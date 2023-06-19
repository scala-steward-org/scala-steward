package org.scalasteward.core.buildtool.scalacli

import munit.CatsEffectSuite
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.data.{GroupId, Repo, Version}
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.util.Nel

class ScalaCliAlgTest extends CatsEffectSuite {
  test("containsBuild: directive in non-source file") {
    val repo = Repo("user", "repo")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val fileWithUsingLib = "test.md" // this test fails if the extension is .scala or .sc
    val grepCmd =
      Cmd.git(repoDir, "grep", "-I", "--fixed-strings", "--files-with-matches", "//> using lib ")
    val initial =
      MockState.empty.copy(commandOutputs = Map(grepCmd -> Right(List(fileWithUsingLib))))
    val obtained = scalaCliAlg.containsBuild(buildRoot).runA(initial)
    assertIO(obtained, false)
  }

  test("getDependencies") {
    val repo = Repo("user", "repo")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val sbtBuildDir = repoDir / "tmp-sbt-build-for-scala-steward"

    val obtained = scalaCliAlg.getDependencies(buildRoot).runS(MockState.empty)
    val expected = MockState.empty.copy(trace =
      Vector(
        Cmd.execSandboxed(
          repoDir,
          "scala-cli",
          "--power",
          "export",
          "--sbt",
          "--output",
          "tmp-sbt-build-for-scala-steward",
          repoDir.toString
        ),
        Cmd("read", s"$sbtBuildDir/project/build.properties"),
        Cmd("test", "-d", s"$sbtBuildDir/project"),
        Cmd("read", "classpath:StewardPlugin_1_3_11.scala"),
        Cmd("write", s"$sbtBuildDir/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd.execSandboxed(
          sbtBuildDir,
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          "-Dsbt.server.forcestart=true",
          s";$crossStewardDependencies"
        ),
        Cmd("rm", "-rf", s"$sbtBuildDir/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd("rm", "-rf", s"$sbtBuildDir")
      )
    )

    assertIO(obtained, expected)
  }

  test("runMigration") {
    val repo = Repo("user", "repo")
    val buildRoot = BuildRoot(repo, ".")
    val migration = ScalafixMigration(
      GroupId("co.fs2"),
      Nel.of("fs2-core"),
      Version("1.0.0"),
      Nel.of("github:functional-streams-for-scala/fs2/v1?sha=v1.0.5")
    )
    val obtained = scalaCliAlg.runMigration(buildRoot, migration).runS(MockState.empty)
    assertIO(obtained.map(_.trace.collect { case Log(_) => () }.size), 1)
  }
}
