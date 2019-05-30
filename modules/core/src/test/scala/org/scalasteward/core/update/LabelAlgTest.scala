package org.scalasteward.core.update

import better.files.File
import cats.implicits._
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.mock.MockContext.labelAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.model.{Label, Update}
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class LabelAlgTest extends FunSuite with Matchers {
  test("attach a single type of label via config labels") {
    val repo = Repo("fthomas", "scala-steward")
    val update = Update.Single("eu.timepit", "refined", "0.8.0", Nel.of("0.8.1"), None)

    val configFile = File.temp / "ws/fthomas/scala-steward/.scala-steward.conf"
    val configContent =
      s"""
         |addLabelsToPullRequests = true
         |labels.library = [ { groupId = "eu.timepit", artifactId = "refined" } ]
         |""".stripMargin
    val parsedConfig =
      "RepoConfig(UpdatesConfig(List(),List()),true,true,LabelsConfig(List(LabelPattern(eu.timepit,Some(refined))),List(),List()))"

    val initialState = MockState.empty.add(configFile, configContent)
    val (state, extendedUpdates) =
      labelAlg.extendUpdatesWithLabels(repo, List(update)).run(initialState).unsafeRunSync()
    val expected = List(update.copy(labels = List(Label.LibraryUpdate).some))

    extendedUpdates shouldBe expected
    state.commands shouldBe Vector(List("read", configFile.toString))
    state.extraEnv shouldBe initialState.extraEnv
    state.files shouldBe initialState.files
    state.logs shouldBe Vector(
      (None, s"Parsed $parsedConfig from $configFile")
    )
  }

  test("attach all types of labels via config labels") {
    val repo = Repo("fthomas", "scala-steward")
    val update1 = Update.Single("org.scoverage", "sbt-scoverage", "1.5.0", Nel.of("1.5.1"), None)
    val update2 = Update.Single("eu.timepit", "refined", "0.8.0", Nel.of("0.8.1"), None)
    val update3 = Update.Single("org.scalatest", "scalatest", "3.0.6", Nel.of("3.0.7"), None)

    val configFile = File.temp / "ws/fthomas/scala-steward/.scala-steward.conf"
    val configContent =
      s"""
         |addLabelsToPullRequests = true
         |labels.library = [ { groupId = "eu.timepit", artifactId = "refined" } ]
         |labels.testLibrary = [ { groupId = "org.scalatest", artifactId = "scalatest" } ]
         |labels.sbtPlugin = [ { groupId = "org.scoverage", artifactId = "sbt-scoverage" } ]
         |""".stripMargin
    val parsedConfig =
      "RepoConfig(UpdatesConfig(List(),List()),true,true,LabelsConfig(List(LabelPattern(eu.timepit,Some(refined))),List(LabelPattern(org.scalatest,Some(scalatest))),List(LabelPattern(org.scoverage,Some(sbt-scoverage)))))"

    val initialState = MockState.empty.add(configFile, configContent)
    val (state, extendedUpdates) =
      labelAlg
        .extendUpdatesWithLabels(repo, List(update1, update2, update3))
        .run(initialState)
        .unsafeRunSync()
    val expected = List(
      update1.copy(labels = List(Label.SbtPluginUpdate).some),
      update2.copy(labels = List(Label.LibraryUpdate).some),
      update3.copy(labels = List(Label.TestLibraryUpdate).some)
    )

    extendedUpdates shouldBe expected
    state.commands shouldBe Vector(
      List("read", configFile.toString),
      List("read", configFile.toString),
      List("read", configFile.toString)
    )
    state.extraEnv shouldBe initialState.extraEnv
    state.files shouldBe initialState.files
    state.logs shouldBe Vector(
      (None, s"Parsed $parsedConfig from $configFile"),
      (None, s"Parsed $parsedConfig from $configFile"),
      (None, s"Parsed $parsedConfig from $configFile")
    )
  }

  test("do not attach labels when feature isn't enabled") {
    val repo = Repo("fthomas", "scala-steward")
    val update = Update.Single("eu.timepit", "refined", "0.8.0", Nel.of("0.8.1"), None)

    val configFile = File.temp / "ws/fthomas/scala-steward/.scala-steward.conf"
    val configContent =
      s"""
         |labels.library = [ { groupId = "eu.timepit", artifactId = "refined" } ]
         |""".stripMargin
    val parsedConfig =
      "RepoConfig(UpdatesConfig(List(),List()),true,false,LabelsConfig(List(LabelPattern(eu.timepit,Some(refined))),List(),List()))"

    val initialState = MockState.empty.add(configFile, configContent)
    val (state, extendedUpdates) =
      labelAlg.extendUpdatesWithLabels(repo, List(update)).run(initialState).unsafeRunSync()

    extendedUpdates shouldBe List(update)
    state.commands shouldBe Vector(List("read", configFile.toString))
    state.extraEnv shouldBe initialState.extraEnv
    state.files shouldBe initialState.files
    state.logs shouldBe Vector(
      (None, s"Parsed $parsedConfig from $configFile")
    )
  }

}
