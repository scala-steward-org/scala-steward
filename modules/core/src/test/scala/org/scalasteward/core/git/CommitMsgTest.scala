package org.scalasteward.core.git

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.Update

class CommitMsgTest extends FunSuite {
  test("Create title for Update.Grouped") {
    val update1 = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val update2 = ("com.example".g % "foo".a % "1.0.0" %> "2.0.0").single

    val update = Update.Grouped("my-group", Some("The PR title"), List(update1, update2))
    assertEquals(CommitMsg.replaceVariables("")(update, None).title, "The PR title")
  }

  test("Create title for Update.Grouped with ${artifactVersions}") {
    val update1 = ("ch.qos.logback".g % "logback-classic".a % "1.2.0" %> "1.2.3").single
    val update2 = ("com.example".g % "foo".a % "1.0.0" %> "2.0.0").single
    val update3 = ("com.example".g % "bar".a % "1.0.0" %> "2.0.0").single
    val update4 = ("com.example".g % "baz".a % "1.0.0" %> "2.0.0").single

    val update = Update.Grouped(
      "my-group",
      Some("Title - ${artifactVersions}"),
      List(update1, update2, update3, update4)
    )
    assertEquals(
      CommitMsg.replaceVariables("")(update, None).title,
      "Title - foo, bar, baz to 2.0.0 - logback-classic to 1.2.3"
    )
  }

  test(
    "Do not create truncated title for Update.Grouped with ${artifactVersions} when 200 characters or less"
  ) {
    val singles = (0 to 23).map(i => ("com.example".g % s"foo-$i".a % "1.0.0" %> "2.0.0").single)
    val update = Update.Grouped("my-group", Some("Title ---- ${artifactVersions}"), singles.toList)
    val title = CommitMsg.replaceVariables("")(update, None).title
    assertEquals(
      title,
      "Title ---- foo-0, foo-1, foo-2, foo-3, foo-4, foo-5, foo-6, foo-7, foo-8, foo-9, foo-10, foo-11, foo-12, foo-13, foo-14, foo-15, foo-16, foo-17, foo-18, foo-19, foo-20, foo-21, foo-22, foo-23 to 2.0.0"
    )
    assertEquals(title.size, 200)
  }

  test(
    "Create truncated title for Update.Grouped with ${artifactVersions} when more than 200 characters"
  ) {
    val singles = (0 to 23).map(i => ("com.example".g % s"foo-$i".a % "1.0.0" %> "2.0.0").single)
    val update = Update.Grouped("my-group", Some("Title ----- ${artifactVersions}"), singles.toList)
    assertEquals(
      CommitMsg.replaceVariables("")(update, None).title,
      "Title ----- foo-0 and 23 more to 2.0.0"
    )
  }
}
