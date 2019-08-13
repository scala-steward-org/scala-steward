package org.scalasteward.core.nurture

import org.scalasteward.core.data.Dependency
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.{FunSuite, Matchers}

class NurtureAlgTest extends FunSuite with Matchers {
  test("getAllDependency") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.owner / repo.repo

    val buildProperties = repoDir / "project" / "build.properties"
    val scalafmtConf = repoDir / ".scalafmt.conf"
    val state1 = MockState.empty
      .add(buildProperties, "sbt.version=1.2.6")
      .add(scalafmtConf, "version=2.0.0")
    val (state2, getDep) = NurtureAlg.getAllDependency(repo).run(state1).unsafeRunSync()
    val (state3, _) = getDep.run(state2).unsafeRunSync()
    val dependencies = getDep.runA(state3).unsafeRunSync()

    dependencies shouldBe List(
      Dependency("org.scala-sbt", "sbt", "sbt", "1.2.6"),
      Dependency("org.scalameta", "scalafmt-core", "scalafmt-core_2.12", "2.0.0"),
      Dependency("org.scalameta", "scalafmt-core", "scalafmt-core_2.13", "2.0.0")
    )
  }

}
