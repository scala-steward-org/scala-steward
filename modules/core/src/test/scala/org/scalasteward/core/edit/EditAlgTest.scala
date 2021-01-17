package org.scalasteward.core.edit

import better.files.File
import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.mock.MockContext.context.editAlg
import org.scalasteward.core.mock.MockContext.{config, envVars}
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafmt.scalafmtBinary
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

class EditAlgTest extends FunSuite {
  private val gitStatus =
    List("git", "status", "--porcelain", "--untracked-files=no", "--ignore-submodules")

  test("applyUpdate") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
    val file1 = repoDir / "build.sbt"
    val file2 = repoDir / "project/Dependencies.scala"

    val state = editAlg
      .applyUpdate(repo, RepoConfig.empty, update)
      .runS(MockState.empty.add(file1, """val catsVersion = "1.2.0"""").add(file2, ""))
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      commands = Vector(
        List("test", "-f", file1.pathAsString),
        List("read", file1.pathAsString),
        List("test", "-f", file2.pathAsString),
        List("read", file2.pathAsString),
        List("read", file1.pathAsString),
        List("read", file1.pathAsString),
        List("read", file1.pathAsString),
        List("write", file1.pathAsString),
        envVars ++ (repoDir.toString :: gitStatus)
      ),
      logs = Vector(
        (None, "Trying heuristic 'moduleId'"),
        (None, "Trying heuristic 'strict'"),
        (None, "Trying heuristic 'original'")
      ),
      files = Map(file1 -> """val catsVersion = "1.3.0"""", file2 -> "")
    )

    assertEquals(state, expected)
  }

  test("applyUpdate with scalafmt update") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.scalameta" % "scalafmt-core" % "2.0.0", Nel.of("2.1.0"))
    val scalafmtConf = repoDir / ".scalafmt.conf"
    val scalafmtConfContent = """maxColumn = 100
                                |version = 2.0.0
                                |align.openParenCallSite = false
                                |""".stripMargin
    val buildSbt = repoDir / "build.sbt"

    val state = editAlg
      .applyUpdate(repo, RepoConfig.empty, update)
      .runS(MockState.empty.add(scalafmtConf, scalafmtConfContent).add(buildSbt, ""))
      .unsafeRunSync()

    val expected = MockState.empty.copy(
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
        List("write", scalafmtConf.pathAsString),
        envVars ++ (repoDir.toString :: gitStatus),
        List("VAR1=val1", "VAR2=val2", repoDir.toString, scalafmtBinary, "--non-interactive"),
        envVars ++ (repoDir.toString :: gitStatus)
      ),
      logs = Vector(
        (None, "Trying heuristic 'moduleId'"),
        (None, "Trying heuristic 'strict'"),
        (None, "Trying heuristic 'original'"),
        (None, "Trying heuristic 'relaxed'"),
        (None, "Trying heuristic 'sliding'"),
        (None, "Trying heuristic 'completeGroupId'"),
        (None, "Trying heuristic 'groupId'"),
        (None, "Trying heuristic 'specific'"),
        (None, "Executing post-update hook for org.scalameta:scalafmt-core")
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

    assertEquals(state, expected)
  }

  test("apply update to ammonite file") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.show
    val update = Update.Single("org.typelevel" % "cats-core" % "1.2.0", Nel.of("1.3.0"))
    val file1 = repoDir / "script.sc"
    val file2 = repoDir / "build.sbt"

    val state = editAlg
      .applyUpdate(repo, RepoConfig.empty, update)
      .runS(
        MockState.empty
          .add(file1, """import $ivy.`org.typelevel::cats-core:1.2.0`, cats.implicits._"""")
          .add(file2, """"org.typelevel" %% "cats-core" % "1.2.0"""")
      )
      .unsafeRunSync()

    val expected = MockState.empty.copy(
      commands = Vector(
        List("test", "-f", file1.pathAsString),
        List("read", file1.pathAsString),
        List("test", "-f", file2.pathAsString),
        List("read", file2.pathAsString),
        List("read", file1.pathAsString),
        List("write", file1.pathAsString),
        List("read", file2.pathAsString),
        List("write", file2.pathAsString),
        envVars ++ (repoDir.toString :: gitStatus)
      ),
      logs = Vector(
        (None, "Trying heuristic 'moduleId'")
      ),
      files = Map(
        file1 -> """import $ivy.`org.typelevel::cats-core:1.3.0`, cats.implicits._"""",
        file2 -> """"org.typelevel" %% "cats-core" % "1.3.0""""
      )
    )

    assertEquals(state, expected)
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
    assertEquals(runApplyUpdate(update, original), expected)
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
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("keyword with extra underscore") {
    val update = Update.Group(
      "org.scala-js" % Nel.of("sbt-scalajs", "scalajs-compiler") % "1.1.0",
      Nel.of("1.1.1")
    )
    val original = Map(
      ".travis.yml" -> """ - SCALA_JS_VERSION=1.1.0""",
      "project/plugins.sbt" -> """val scalaJsVersion = Option(System.getenv("SCALA_JS_VERSION")).getOrElse("1.1.0")"""
    )
    val expected = Map(
      ".travis.yml" -> """ - SCALA_JS_VERSION=1.1.1""",
      "project/plugins.sbt" -> """val scalaJsVersion = Option(System.getenv("SCALA_JS_VERSION")).getOrElse("1.1.1")"""
    )
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("test updating group id and version") {
    val update = Update.Single(
      crossDependency = "com.github.mpilquist" % "simulacrum" % "0.19.0",
      newerVersions = Nel.of("1.0.0"),
      newerGroupId = Some(GroupId("org.typelevel")),
      newerArtifactId = Some("simulacrum")
    )
    val original = Map(
      "build.sbt" ->
        """
          |val simulacrum = "0.19.0"
          |"com.github.mpilquist" %% "simulacrum" % simulacrum
          |"""".stripMargin
    )
    val expected = Map(
      "build.sbt" ->
        """
          |val simulacrum = "1.0.0"
          |"org.typelevel" %% "simulacrum" % simulacrum
          |"""".stripMargin // the version should have been updated here
    )
    assertEquals(runApplyUpdate(update, original), expected)
  }

  test("test updating artifact id and version") {
    val update = Update.Single(
      crossDependency = "com.test" % "artifact" % "1.0.0",
      newerVersions = Nel.of("2.0.0"),
      newerGroupId = Some(GroupId("com.test")),
      newerArtifactId = Some("newer-artifact")
    )
    val original = Map(
      "Dependencies.scala" ->
        """
          |private val artifactVersion = "1.0.0"
          |val test = "com.test" %% "artifact" % testVersion 
          |"""".stripMargin
    )
    val expected = Map(
      "Dependencies.scala" ->
        """
          |private val artifactVersion = "2.0.0"
          |val test = "com.test" %% "newer-artifact" % testVersion 
          |"""".stripMargin
    )
    assertEquals(runApplyUpdate(update, original), expected)
  }

  private def runApplyUpdate(update: Update, files: Map[String, String]): Map[String, String] = {
    val repoDir = File.temp / "ws/owner/repo"
    val filesInRepoDir = files.map { case (file, content) => repoDir / file -> content }
    editAlg
      .applyUpdate(Repo("owner", "repo"), RepoConfig.empty, update)
      .runS(MockState.empty.addFiles(filesInRepoDir))
      .map(_.files)
      .unsafeRunSync()
      .map { case (file, content) => file.toString.replace(repoDir.toString + "/", "") -> content }
  }
}
