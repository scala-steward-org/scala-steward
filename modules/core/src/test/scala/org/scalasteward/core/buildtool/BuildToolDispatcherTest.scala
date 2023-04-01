package org.scalasteward.core.buildtool

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.data._
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.repoconfig.{BuildRootConfig, RepoConfig}
import org.scalasteward.core.scalafmt
import org.scalasteward.core.scalafmt.scalafmtConfName

class BuildToolDispatcherTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("build-tool-dispatcher", "test-1")
    val repoConfig = RepoConfig.empty.copy(buildRoots =
      Some(List(BuildRootConfig("."), BuildRootConfig("mvn-build")))
    )
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val initial = MockState.empty
      .addFiles(
        repoDir / "mvn-build" / "pom.xml" -> "",
        repoDir / "project" / "build.properties" -> "sbt.version=1.2.6",
        repoDir / scalafmtConfName -> "version=2.0.0"
      )
      .unsafeRunSync()
    val (state, deps) =
      buildToolDispatcher.getDependencies(repo, repoConfig).runSA(initial).unsafeRunSync()

    val expectedState = initial.copy(trace =
      Vector(
        Cmd("test", "-f", s"$repoDir/pom.xml"),
        Cmd("test", "-f", s"$repoDir/build.sc"),
        Cmd("test", "-f", s"$repoDir/build.sbt"),
        Cmd.git(
          repoDir,
          "grep",
          "-I",
          "--fixed-strings",
          "--files-with-matches",
          "//> using lib "
        ),
        Cmd("test", "-f", s"$repoDir/mvn-build/pom.xml"),
        Cmd("test", "-f", s"$repoDir/mvn-build/build.sc"),
        Cmd("test", "-f", s"$repoDir/mvn-build/build.sbt"),
        Cmd.git(
          repoDir,
          "grep",
          "-I",
          "--fixed-strings",
          "--files-with-matches",
          "//> using lib "
        ),
        Log("Get dependencies in . from sbt"),
        Cmd("read", s"$repoDir/project/build.properties"),
        Cmd("test", "-d", s"$repoDir/project"),
        Cmd("test", "-d", s"$repoDir/project/project"),
        Cmd("read", "classpath:StewardPlugin_1_0_0.scala"),
        Cmd("write", s"$repoDir/project/scala-steward-StewardPlugin_1_0_0.scala"),
        Cmd("write", s"$repoDir/project/project/scala-steward-StewardPlugin_1_0_0.scala"),
        Cmd.execSandboxed(
          repoDir,
          "sbt",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          s";$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        ),
        Cmd("rm", "-rf", s"$repoDir/project/project/scala-steward-StewardPlugin_1_0_0.scala"),
        Cmd("rm", "-rf", s"$repoDir/project/scala-steward-StewardPlugin_1_0_0.scala"),
        Cmd("read", s"$repoDir/$scalafmtConfName"),
        Log("Get dependencies in mvn-build from Maven"),
        Cmd.execSandboxed(
          repoDir / "mvn-build",
          "mvn",
          maven.args.batchMode,
          maven.command.listDependencies,
          maven.args.excludeTransitive
        ),
        Cmd.execSandboxed(
          repoDir / "mvn-build",
          "mvn",
          maven.args.batchMode,
          maven.command.listRepositories
        ),
        Cmd("read", s"$repoDir/mvn-build/$scalafmtConfName")
      )
    )

    assertEquals(state, expectedState)

    val expectedDeps = List(
      Scope(
        List(
          sbt.sbtDependency(Version("1.2.6")).get,
          scalafmt.scalafmtDependency(Version("2.0.0"))
        ),
        List(Resolver.mavenCentral)
      ),
      Scope(List.empty[Dependency], Nil)
    )
    assertEquals(deps, expectedDeps)
  }
}
