package org.scalasteward.core.edit

import better.files.File
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Update
import org.scalasteward.core.mock.MockContext.{config, editAlg}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repoconfig.UpdatesConfig
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EditAlgTest extends AnyFunSuite with Matchers {
  test("applyUpdate") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
    val file1 = repoDir / "build.sbt"
    val file2 = repoDir / "project/Dependencies.scala"

    val state = editAlg
      .applyUpdate(repo, update, UpdatesConfig.defaultFileExtensions)
      .runS(MockState.empty.add(file1, """val catsVersion = "1.2.0"""").add(file2, ""))
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("test", "-f", file1.pathAsString),
        List("read", file1.pathAsString),
        List("test", "-f", file2.pathAsString),
        List("read", file2.pathAsString),
        List("read", file1.pathAsString),
        List("read", file1.pathAsString),
        List("read", file1.pathAsString),
        List("write", file1.pathAsString)
      ),
      logs = Vector(
        (None, "Trying heuristic 'moduleId'"),
        (None, "Trying heuristic 'strict'"),
        (None, "Trying heuristic 'original'")
      ),
      files = Map(file1 -> """val catsVersion = "1.3.0"""", file2 -> "")
    )
  }

  test("applyUpdate with scalafmt update") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.scalameta" % "scalafmt-core" % "2.0.0", Nel.of("2.1.0"))
    val scalafmtConf = repoDir / ".scalafmt.conf"
    val buildSbt = repoDir / "build.sbt"

    val state = editAlg
      .applyUpdate(repo, update, UpdatesConfig.defaultFileExtensions)
      .runS(
        MockState.empty
          .add(
            scalafmtConf,
            """maxColumn = 100
              |version = 2.0.0
              |align.openParenCallSite = false
              |""".stripMargin
          )
          .add(buildSbt, "")
      )
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("test", "-f", scalafmtConf.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("test", "-f", buildSbt.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("read", scalafmtConf.pathAsString),
        List("write", scalafmtConf.pathAsString)
      ),
      logs = Vector(
        (None, "Trying heuristic 'moduleId'"),
        (None, "Trying heuristic 'strict'"),
        (None, "Trying heuristic 'original'"),
        (None, "Trying heuristic 'relaxed'"),
        (None, "Trying heuristic 'sliding'"),
        (None, "Trying heuristic 'completeGroupId'"),
        (None, "Trying heuristic 'groupId'"),
        (None, "Trying heuristic 'specific'")
      ),
      files = Map(
        scalafmtConf ->
          """maxColumn = 100
            |version = 2.1.0
            |align.openParenCallSite = false
            |""".stripMargin,
        buildSbt -> ""
      )
    )
  }

  test("apply update to ammonite file") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
    val file1 = repoDir / "script.sc"
    val file2 = repoDir / "build.sbt"

    val state = editAlg
      .applyUpdate(repo, update, UpdatesConfig.defaultFileExtensions)
      .runS(
        MockState.empty
          .add(file1, """import $ivy.`org.typelevel::cats-core:1.2.0`, cats.implicits._"""")
          .add(file2, """"org.typelevel" %% "cats-core" % "1.2.0"""")
      )
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("test", "-f", file1.pathAsString),
        List("read", file1.pathAsString),
        List("test", "-f", file2.pathAsString),
        List("read", file2.pathAsString),
        List("read", file1.pathAsString),
        List("write", file1.pathAsString),
        List("read", file2.pathAsString),
        List("write", file2.pathAsString)
      ),
      logs = Vector(
        (None, "Trying heuristic 'moduleId'")
      ),
      files = Map(
        file1 -> """import $ivy.`org.typelevel::cats-core:1.3.0`, cats.implicits._"""",
        file2 -> """"org.typelevel" %% "cats-core" % "1.3.0""""
      )
    )
  }

  def runApplyUpdate(update: Update, files: Map[String, String]): Map[String, String] = {
    val repoDir = File.temp / "ws/owner/repo"
    val filesInRepoDir = files.map { case (file, content) => repoDir / file -> content }
    editAlg
      .applyUpdate(Repo("owner", "repo"), update, UpdatesConfig.defaultFileExtensions)
      .runS(MockState.empty.addFiles(filesInRepoDir))
      .map(_.files)
      .unsafeRunSync()
      .map { case (file, content) => file.toString.replace(repoDir.toString + "/", "") -> content }
  }

  test("reproduce https://github.com/circe/circe-config/pull/40") {
    val update = Update.Single("com.typesafe" % "config" % "1.3.3", Nel.of("1.3.4"))
    val original = Map(
      "build.sbt" -> """val config = "1.3.3"""",
      "project/plugins.sbt" -> """addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")"""
    )
    val expected = Map(
      "build.sbt" -> """val config = "1.3.3"""", // the version should have been updated here
      "project/plugins.sbt" -> """addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.4")"""
    )
    runApplyUpdate(update, original) shouldBe expected
  }

  test("file restriction when sbt update") {
    val update = Update.Single("org.scala-sbt" % "sbt" % "1.1.2", Nel.of("1.2.8"))
    val original = Map(
      "build.properties" -> """sbt.version=1.1.2""",
      "project/plugins.sbt" -> """addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")"""
    )
    val expected = Map(
      "build.properties" -> """sbt.version=1.2.8""",
      "project/plugins.sbt" -> """addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")"""
    )
    runApplyUpdate(update, original) shouldBe expected
  }
}
