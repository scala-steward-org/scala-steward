package org.scalasteward.core.forge

import better.files.File
import munit.FunSuite
import org.http4s.Uri
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.{Repo, Update}
import org.scalasteward.core.forge.Forge.{GitHub, GitLab}
import org.scalasteward.core.git

class ForgeTypeTest extends FunSuite {
  private val repo = Repo("foo", "bar")
  private val dummyGitHub = GitHub(Uri.unsafeFromString(""), false, false, 0L, File(""))
  private val dummyGitLab =
    GitLab(Uri.unsafeFromString(""), "", File.newTemporaryFile(), false, false, false, None, false)

  // Single updates
  {
    val update = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val updateBranch = git.branchFor(update, None)

    test("headFor (single)") {
      assertEquals(dummyGitHub.pullRequestHeadFor(repo, updateBranch), s"foo:${updateBranch.name}")
      assertEquals(dummyGitLab.pullRequestHeadFor(repo, updateBranch), updateBranch.name)
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
      assertEquals(dummyGitHub.pullRequestHeadFor(repo, updateBranch), s"foo:update/my-group")
      assertEquals(dummyGitLab.pullRequestHeadFor(repo, updateBranch), updateBranch.name)
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
      assertEquals(
        dummyGitHub.pullRequestHeadFor(repo, updateBranch),
        s"foo:update/my-group-1164623676"
      )
      assertEquals(dummyGitLab.pullRequestHeadFor(repo, updateBranch), updateBranch.name)
    }
  }

}
