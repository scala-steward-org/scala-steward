package org.scalasteward.core.buildtool.maven

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.scalasteward.core.mock.MockContext.context.mavenAlg
import org.scalasteward.core.mock.MockState.TraceEntry.Cmd
import org.scalasteward.core.mock.{MockConfig, MockState}
import org.scalasteward.core.vcs.data.{BuildRoot, Repo}

class MavenAlgTest extends FunSuite {
  test("getDependencies") {
    val repo = Repo("namespace", "repo-name")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = MockConfig.config.workspace / repo.toPath

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
          "--batch-mode",
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
          "--batch-mode",
          command.listRepositories
        )
      )
    )

    assertEquals(state, expected)
  }
}
