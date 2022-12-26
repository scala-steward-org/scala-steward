package org.scalasteward.core.buildtool

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.data.{Dependency, Resolver, Scope, Version}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.buildToolDispatcher
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.repoconfig.{BuildRootConfig, RepoConfig}
import org.scalasteward.core.scalafmt
import org.scalasteward.core.scalafmt.scalafmtConfName
import org.scalasteward.core.vcs.data.Repo

class BuildToolDispatcherTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("build-tool-dispatcher", "test-1")
    val repoConfig = RepoConfig.empty.copy(buildRoots =
      Some(List(BuildRootConfig("."), BuildRootConfig("mvn-build")))
    )
    val repoDir = config.workspace / repo.toPath
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
        Cmd("test", "-f", s"$repoDir/mvn-build/pom.xml"),
        Cmd("test", "-f", s"$repoDir/mvn-build/build.sc"),
        Cmd("test", "-f", s"$repoDir/mvn-build/build.sbt"),
        Log("Get dependencies in . from sbt"),
        Cmd("read", s"$repoDir/project/build.properties"),
        Cmd("read", "classpath:org/scalasteward/sbt/plugin/StewardPlugin_1_0_0.scala"),
        Cmd("write", s"$repoDir/project/scala-steward-StewardPlugin_1_0_0.scala"),
        Cmd("write", s"$repoDir/project/project/scala-steward-StewardPlugin_1_0_0.scala"),
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
        Cmd("rm", "-rf", s"$repoDir/project/project/scala-steward-StewardPlugin_1_0_0.scala"),
        Cmd("rm", "-rf", s"$repoDir/project/scala-steward-StewardPlugin_1_0_0.scala"),
        Cmd("read", s"$repoDir/$scalafmtConfName"),
        Log("Get dependencies in mvn-build from Maven"),
        Cmd(
          s"$repoDir/mvn-build",
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir/mvn-build",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "mvn",
          maven.args.batchMode,
          maven.command.listDependencies,
          maven.args.excludeTransitive
        ),
        Cmd(
          s"$repoDir/mvn-build",
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir/mvn-build",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
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
