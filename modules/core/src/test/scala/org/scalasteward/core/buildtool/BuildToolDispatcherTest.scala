package org.scalasteward.core.buildtool

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data.{Resolver, Scope, Version}
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.buildToolDispatcher
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt
import org.scalasteward.core.scalafmt.scalafmtConfName
import org.scalasteward.core.vcs.data.Repo

class BuildToolDispatcherTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("build-tool-dispatcher", "test-1")
    val repoConfig = RepoConfig.empty
    val repoDir = config.workspace / repo.toPath
    val initial = MockState.empty
      .addFiles(
        repoDir / "project" / "build.properties" -> "sbt.version=1.2.6",
        repoDir / scalafmtConfName -> "version=2.0.0"
      )
      .unsafeRunSync()
    val (state, deps) =
      buildToolDispatcher.getDependencies(repo, repoConfig).runSA(initial).unsafeRunSync()

    val expectedState = initial.copy(trace =
      Vector(
        Cmd("test", "-f", s"$repoDir/pom.xml"),
        Cmd("test", "-f", s"$repoDir/build.gradle"),
        Cmd("test", "-f", s"$repoDir/build.sc"),
        Cmd("test", "-f", s"$repoDir/build.sbt"),
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
        Cmd("read", s"$repoDir/project/build.properties"),
        Cmd("read", s"$repoDir/$scalafmtConfName")
      )
    )

    assertEquals(state, expectedState)

    val expectedDeps = List(
      Scope(
        List(
          sbt.sbtDependency(SbtVersion("1.2.6")).get,
          scalafmt.scalafmtDependency(Version("2.0.0"))
        ),
        List(Resolver.mavenCentral)
      )
    )
    assertEquals(deps, expectedDeps)
  }
}
