package org.scalasteward.core.buildtool

import munit.FunSuite
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data.{Resolver, Scope, Version}
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.mock.MockContext.context.buildToolDispatcher
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.scalafmt
import org.scalasteward.core.vcs.data.Repo

class BuildToolDispatcherTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("typelevel", "cats")
    val repoDir = config.workspace / repo.show
    val files = Map(
      repoDir / "project" / "build.properties" -> "sbt.version=1.2.6",
      repoDir / ".scalafmt.conf" -> "version=2.0.0"
    )
    val initial = MockState.empty.copy(files = files)
    val (state, deps) =
      buildToolDispatcher.getDependencies(repo).run(initial).unsafeRunSync()

    val expectedState = initial.copy(commands =
      Vector(
        List("read", s"$repoDir/.scala-steward.conf"),
        List("read", s"/tmp/default.scala-steward.conf"),
        List("test", "-f", s"$repoDir/pom.xml"),
        List("test", "-f", s"$repoDir/build.sc"),
        List("test", "-f", s"$repoDir/build.sbt"),
        List(
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
        List("read", s"$repoDir/project/build.properties"),
        List("read", s"$repoDir/.scalafmt.conf")
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
