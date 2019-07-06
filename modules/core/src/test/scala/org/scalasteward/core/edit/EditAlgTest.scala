package org.scalasteward.core.edit

import better.files.File
import org.scalasteward.core.mock.MockContext.editAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.model.Update
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.{FunSuite, Matchers}

class EditAlgTest extends FunSuite with Matchers {

  test("applyUpdate") {
    val repo = Repo("fthomas", "scala-steward")
    val update = Update.Single("org.typelevel", "cats-core", "1.2.0", Nel.of("1.3.0"))
    val file1 = File.temp / "ws/fthomas/scala-steward/build.sbt"
    val file2 = File.temp / "ws/fthomas/scala-steward/project/Dependencies.scala"

    val state = editAlg
      .applyUpdate(repo, update)
      .runS(MockState.empty.add(file1, """val catsVersion = "1.2.0"""").add(file2, ""))
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", file1.pathAsString),
        List("read", file2.pathAsString),
        List("read", file1.pathAsString),
        List("read", file1.pathAsString),
        List("read", file1.pathAsString),
        List("write", file1.pathAsString)
      ),
      logs = Vector(
        (None, "Trying heuristic 'strict'"),
        (None, "Trying heuristic 'sbt'"),
        (None, "Trying heuristic 'original'")
      ),
      files = Map(file1 -> """val catsVersion = "1.3.0"""", file2 -> "")
    )
  }

  def runApplyUpdate(update: Update, files: Map[String, String]): Map[String, String] = {
    val repoDir = File.temp / "ws/owner/repo"
    val filesInRepoDir = files.map { case (file, content) => repoDir / file -> content }
    editAlg
      .applyUpdate(Repo("owner", "repo"), update)
      .runS(MockState.empty.addFiles(filesInRepoDir))
      .map(_.files)
      .unsafeRunSync()
      .map { case (file, content) => file.toString.replace(repoDir.toString + "/", "") -> content }
  }

  test("reproduce https://github.com/circe/circe-config/pull/40") {
    val update = Update.Single("com.typesafe", "config", "1.3.3", Nel.of("1.3.4"))
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
}
