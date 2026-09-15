package org.scalasteward.core.buildtool

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.sbt.command.*
import org.scalasteward.core.buildtool.scalacli.ScalaCliAlg
import org.scalasteward.core.data.*
import org.scalasteward.core.mock.MockContext.context.*
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.mock.{MockEffOps, MockState}
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

    val allGreps = ScalaCliAlg.directives.map { search =>
      Cmd.git(
        repoDir,
        "grep",
        "-I",
        "--fixed-strings",
        "--files-with-matches",
        search
      )
    }

    val expectedState = initial.copy(trace =
      Cmd("test", "-d", s"$repoDir/src/main/g8") +:
        Cmd("test", "-f", s"$repoDir/gradle/libs.versions.toml") +:
        Cmd("test", "-f", s"$repoDir/pom.xml") +:
        Cmd("test", "-f", s"$repoDir/build.mill") +:
        Cmd("test", "-f", s"$repoDir/build.mill.scala") +:
        Cmd("test", "-f", s"$repoDir/build.sc") +:
        Cmd("test", "-f", s"$repoDir/build.sbt") +:
        allGreps ++:
        Cmd("test", "-f", s"$repoDir/mvn-build/gradle/libs.versions.toml") +:
        Cmd("test", "-f", s"$repoDir/mvn-build/pom.xml") +:
        Cmd("test", "-f", s"$repoDir/mvn-build/build.mill") +:
        Cmd("test", "-f", s"$repoDir/mvn-build/build.mill.scala") +:
        Cmd("test", "-f", s"$repoDir/mvn-build/build.sc") +:
        Cmd("test", "-f", s"$repoDir/mvn-build/build.sbt") +:
        allGreps ++:
        Log("Get dependencies in . from sbt") +:
        Cmd("read", s"$repoDir/project/build.properties") +:
        Cmd("test", "-d", s"$repoDir/project") +:
        Cmd("test", "-d", s"$repoDir/project/project") +:
        Cmd("read", "classpath:StewardPlugin_1_0_0.scala") +:
        Cmd("write", s"$repoDir/project/scala-steward-StewardPlugin_1_0_0.scala") +:
        Cmd("write", s"$repoDir/project/project/scala-steward-StewardPlugin_1_0_0.scala") +:
        Cmd.execSandboxed(
          repoDir,
          "sbt",
          "--server",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          "-Dsbt.server.forcestart=true",
          s";$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        ) +:
        Cmd("rm", "-rf", s"$repoDir/project/project/scala-steward-StewardPlugin_1_0_0.scala") +:
        Cmd("rm", "-rf", s"$repoDir/project/scala-steward-StewardPlugin_1_0_0.scala") +:
        Cmd("read", s"$repoDir/$scalafmtConfName") +:
        Log("Get dependencies in mvn-build from Maven") +:
        Cmd.execSandboxed(
          repoDir / "mvn-build",
          "mvn",
          maven.args.batchMode,
          maven.command.listDependencies,
          maven.args.excludeTransitive
        ) +:
        Cmd.execSandboxed(
          repoDir / "mvn-build",
          "mvn",
          maven.args.batchMode,
          maven.command.listRepositories
        ) +:
        Cmd("read", s"$repoDir/mvn-build/$scalafmtConfName") +:
        Vector.empty[MockState.TraceEntry]
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

  test("getDependencies: renders Giter8 templates before extracting dependencies") {
    val repo = Repo("example", "kafka-streams.g8")
    val repoConfig = RepoConfig.empty.copy(buildRoots = Some(Nil))
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val renderCommand = Cmd.execSandboxed(
      repoDir,
      "sbt",
      "--server",
      "-Dsbt.color=false",
      "-Dsbt.log.noformat=true",
      "-Dsbt.supershell=false",
      "-Dsbt.server.forcestart=true",
      ";g8"
    )
    val renderedFiles = List(
      repoDir / "target" / "g8" / "build.sbt" -> "lazy val root = ...",
      repoDir / "target" / "g8" / "project" / "build.properties" -> "sbt.version=1.2.6"
    )
    val initial = MockState.empty
      .copy(commandFileWrites = Map(renderCommand -> renderedFiles))
      .addFiles(
        repoDir / "src" / "main" / "g8" / "build.sbt" -> "lazy val root = ..."
      )
      .unsafeRunSync()
    val (state, deps) =
      buildToolDispatcher.getDependencies(repo, repoConfig).runSA(initial).unsafeRunSync()

    assert(deps.nonEmpty)
    val renderIndex = state.trace.indexOf(Log("Render Giter8 template in src/main/g8"))
    val extractionIndex = state.trace.indexOf(Log("Get dependencies in target/g8 from sbt"))
    assert(renderIndex >= 0)
    assert(extractionIndex > renderIndex)
    assert(
      state.trace.contains(
        Cmd.execSandboxed(
          repoDir / "target" / "g8",
          "sbt",
          "--server",
          "-Dsbt.color=false",
          "-Dsbt.log.noformat=true",
          "-Dsbt.supershell=false",
          "-Dsbt.server.forcestart=true",
          s";$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        )
      )
    )
  }

  test("getDependencies: no giter8 template returns only base build roots") {
    val repo = Repo("example", "regular-project")
    val repoConfig = RepoConfig.empty
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()
    val initial = MockState.empty
      .addFiles(
        repoDir / "build.sbt" -> "lazy val root = ...",
        repoDir / "project" / "build.properties" -> "sbt.version=1.2.6",
        repoDir / scalafmtConfName -> "version=2.0.0"
      )
      .unsafeRunSync()
    val deps = buildToolDispatcher.getDependencies(repo, repoConfig).runA(initial).unsafeRunSync()

    assertEquals(deps.length, 1)
  }
}
