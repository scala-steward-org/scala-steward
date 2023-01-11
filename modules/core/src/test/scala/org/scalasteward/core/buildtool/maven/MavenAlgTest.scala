package org.scalasteward.core.buildtool.maven

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.forge.data.{BuildRoot, Repo}
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd

class MavenAlgTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("namespace", "repo-name")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()

    val state = mavenAlg.getDependencies(buildRoot).runS(MockState.empty).unsafeRunSync()
    val expected = MockState.empty.copy(
      trace = Vector(
        Cmd(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "mvn",
          args.batchMode,
          command.listDependencies,
          args.excludeTransitive
        ),
        Cmd(
          repoDir.toString,
          "firejail",
          "--quiet",
          s"--whitelist=$repoDir",
          "--env=VAR1=val1",
          "--env=VAR2=val2",
          "mvn",
          args.batchMode,
          command.listRepositories
        )
      )
    )

    assertEquals(state, expected)
  }
}
