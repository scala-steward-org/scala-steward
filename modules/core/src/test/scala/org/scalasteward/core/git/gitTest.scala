package org.scalasteward.core.git

import munit.ScalaCheckSuite
import org.scalacheck.Prop.*
import org.scalasteward.core.TestInstances.*
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.Update
import org.scalasteward.core.repoconfig.CommitsConfig
import org.scalasteward.core.update.show

class gitTest extends ScalaCheckSuite {
  test("branchFor uses the default update branch prefix") {
    val update = ("org.typelevel".g % "cats-effect".a % "3.3.0" %> "3.4.0").single

    assertEquals(branchFor(update), Branch("update/cats-effect-3.4.0"))
  }

  test("branchFor uses a custom update branch prefix") {
    val update = ("org.typelevel".g % "cats-effect".a % "3.3.0" %> "3.4.0").single

    assertEquals(
      branchFor(update, updateBranchPrefix = "scala-steward"),
      Branch("scala-steward/cats-effect-3.4.0")
    )
  }

  test("branchFor ignores trailing slashes in custom update branch prefixes") {
    val update = ("org.typelevel".g % "cats-effect".a % "3.3.0" %> "3.4.0").single

    assertEquals(
      branchFor(update, updateBranchPrefix = "scala-steward/"),
      Branch("scala-steward/cats-effect-3.4.0")
    )
  }

  test("branchFor uses custom prefixes with non-default base branches") {
    val update = ("org.typelevel".g % "cats-effect".a % "3.3.0" %> "3.4.0").single

    assertEquals(
      branchFor(update, Some(Branch("series/0.6.x")), "scala-steward"),
      Branch("scala-steward/series/0.6.x/cats-effect-3.4.0")
    )
  }

  test("branchFor uses custom prefixes for grouped updates") {
    val update1 = ("org.typelevel".g % "cats-effect".a % "3.3.0" %> "3.4.0").single
    val update2 = ("org.typelevel".g % "cats-core".a % "2.8.0" %> "2.9.0").single
    val grouped = Update.Grouped("typelevel", Some("Typelevel updates"), List(update1, update2))

    assertEquals(
      branchFor(grouped, updateBranchPrefix = "scala-steward"),
      Branch("scala-steward/typelevel")
    )
  }

  test("branchFor replaces hashes in grouped update branches with custom prefixes") {
    val update1 = ("org.typelevel".g % "cats-effect".a % "3.3.0" %> "3.4.0").single
    val update2 = ("org.typelevel".g % "cats-core".a % "2.8.0" %> "2.9.0").single
    val grouped = Update.Grouped("typelevel-${hash}", None, List(update1, update2))
    val hashString =
      Math.abs(grouped.updates.map(_.nextVersion).sortBy(_.value).hashCode()).toString

    assertEquals(
      branchFor(grouped, updateBranchPrefix = "scala-steward"),
      Branch(s"scala-steward/typelevel-$hashString")
    )
  }

  test("commitMsgFor should work with static message") {
    val commitsConfig = CommitsConfig(Some("Static message"))
    forAll { (update: Update.Single) =>
      assertEquals(commitMsgFor(update, commitsConfig, None).title, "Static message")
    }
  }

  test("commitMsgFor adds branch if provided") {
    val commitsConfig = CommitsConfig(Some(s"$${default}"))
    val branch = Branch("some-branch")
    forAll { (update: Update.Single) =>
      val expected = s"Update ${show.oneLiner(update)} to ${update.nextVersion} in ${branch.name}"
      assertEquals(commitMsgFor(update, commitsConfig, Some(branch)).title, expected)
    }
  }

  test("commitMsgFor should work with default message") {
    val commitsConfig = CommitsConfig(Some(s"$${default}"))
    forAll { (update: Update.Single) =>
      val expected = s"Update ${show.oneLiner(update)} to ${update.nextVersion}"
      assertEquals(commitMsgFor(update, commitsConfig, None).title, expected)
    }
  }

  test("commitMsgFor should work with templated message") {
    val commitsConfig =
      CommitsConfig(Some(s"Update $${artifactName} from $${currentVersion} to $${nextVersion}"))
    forAll { (update: Update.Single) =>
      val expected =
        s"Update ${show.oneLiner(update)} from ${update.currentVersion} to ${update.nextVersion}"
      assertEquals(commitMsgFor(update, commitsConfig, None).title, expected)
    }
  }

  test("commitMsgFor should work with templated message and non-default branch") {
    val commitsConfig =
      CommitsConfig(
        Some(
          s"Update $${artifactName} from $${currentVersion} to $${nextVersion} in $${branchName}"
        )
      )
    val branch = Branch("some-branch")
    forAll { (update: Update.Single) =>
      val expected =
        s"Update ${show.oneLiner(update)} from ${update.currentVersion} to ${update.nextVersion} in ${branch.name}"
      assertEquals(commitMsgFor(update, commitsConfig, Some(branch)).title, expected)
    }
  }
}
