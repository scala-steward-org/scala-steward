package org.scalasteward.core.buildtool.scalacli

import munit.CatsEffectSuite
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.data.Repo
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd

class ScalaCliAlgTest extends CatsEffectSuite {
  test("getDependencies") {
    val repo = Repo("user", "repo")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val sbtBuildDir = repoDir / "tmp-sbt-build-for-scala-steward"

    val obtained = scalaCliAlg.getDependencies(buildRoot).runS(MockState.empty)
    val expected = MockState.empty.copy(trace =
      Vector(
        Cmd(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "scala-cli",
          "export",
          "--sbt",
          "--output",
          "tmp-sbt-build-for-scala-steward",
          repoDir.toString
        ),
        Cmd("read", s"$sbtBuildDir/project/build.properties"),
        Cmd("read", "classpath:StewardPlugin_1_3_11.scala"),
        Cmd("write", s"$sbtBuildDir/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd("write", s"$sbtBuildDir/project/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd(
          sbtBuildDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$sbtBuildDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          s";$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        ),
        Cmd("rm", "-rf", s"$sbtBuildDir/project/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd("rm", "-rf", s"$sbtBuildDir/project/scala-steward-StewardPlugin_1_3_11.scala"),
        Cmd("rm", "-rf", s"$sbtBuildDir")
      )
    )

    assertIO(obtained, expected)
  }
}
