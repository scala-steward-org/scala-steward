package org.scalasteward.core.forge

import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{Repo, Update}
import org.scalasteward.core.forge.ForgeType.{GitHub, GitLab}
import org.scalasteward.core.git

class ForgePackageTest extends FunSuite {
  private val repo = Repo("foo", "bar")

  // Single updates

  {

    val update = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val updateBranch = git.branchFor(update, None)

    test("headFor (single)") {
      assertEquals(headFor(GitHub, repo, updateBranch), s"foo:${updateBranch.name}")
      assertEquals(headFor(GitLab, repo, updateBranch), updateBranch.name)
    }

  }

  // Grouped updates

  {

    val update = Update.Grouped(
      name = "my-group",
      title = None,
      updates = List(("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single)
    )

    val updateBranch = git.branchFor(update, None)

    test("headFor (grouped)") {
      assertEquals(headFor(GitHub, repo, updateBranch), s"foo:update/my-group")
      assertEquals(headFor(GitLab, repo, updateBranch), updateBranch.name)
    }
  }

  // Grouped updates with hash

  {
    val update = Update.Grouped(
      name = "my-group-${hash}",
      title = None,
      updates = List(("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single)
    )

    val updateBranch = git.branchFor(update, None)

    test("headFor (grouped) with $hash") {
      assertEquals(headFor(GitHub, repo, updateBranch), s"foo:update/my-group-1164623676")
      assertEquals(headFor(GitLab, repo, updateBranch), updateBranch.name)
    }
  }

}
