package org.scalasteward.core.nurture

import better.files.File
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockContext.editAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.model.Update
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class EditAlgTest extends FunSuite with Matchers {

  test("applyUpdate") {
    val repo = Repo("fthomas", "scala-steward")
    val update = Update.Single("org.typelevel", "cats-core", "1.2.0", Nel.of("1.3.0"), None)
    val file = File.temp / "ws/fthomas/scala-steward/build.sbt"

    val state = editAlg
      .applyUpdate(repo, update)
      .runS(MockState.empty.add(file, """val catsVersion = "1.2.0""""))
      .unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", file.pathAsString),
        List("read", file.pathAsString),
        List("write", file.pathAsString)
      ),
      logs = Vector(
        (None, "Trying update strategy 'replaceAllInStrict'"),
        (None, "Trying update strategy 'replaceAllIn'")
      ),
      files = Map(file -> """val catsVersion = "1.3.0"""")
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
