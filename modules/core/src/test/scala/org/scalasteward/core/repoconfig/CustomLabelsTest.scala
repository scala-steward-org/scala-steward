/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.repoconfig

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.Update
import org.scalasteward.core.util.Nel

class CustomLabelsTest extends FunSuite {
  private val config: RepoConfig = RepoConfigAlg
    .parseRepoConfig(
      """|pullRequests.customLabels = [ "dependencies" ]
         |dependencyOverrides = [
         |  {
         |    dependency = { groupId = "com.example", artifactId = "frequency-only" },
         |    pullRequests = { frequency = "1 day" }
         |  },
         |  {
         |    dependency = { groupId = "com.example", artifactId = "no-labels" },
         |    pullRequests = { customLabels = [] }
         |  },
         |  {
         |    dependency = { groupId = "com.example", artifactId = "foo", version = "2." },
         |    pullRequests = { customLabels = [ "foo-v2" ] }
         |  },
         |  {
         |    dependency = { groupId = "com.example" },
         |    pullRequests = { customLabels = [ "example" ] }
         |  }
         |]
         |""".stripMargin
    )
    .fold(err => fail(s"Failed to parse config: $err"), identity)

  test("repo-wide labels are used when nothing more specific matches") {
    val update = ("org.other".g % "lib".a % "1.0.0" %> "1.1.0").single
    assertEquals(config.customLabelsFor(update), List("dependencies"))
  }

  test("dependencyOverrides customLabels replace repo-wide labels") {
    val update = ("com.example".g % "bar".a % "1.0.0" %> "1.1.0").single
    assertEquals(config.customLabelsFor(update), List("example"))
  }

  test("first matching dependencyOverrides entry wins, including version patterns") {
    val v2 = ("com.example".g % "foo".a % "1.0.0" %> "2.0.0").single
    val v1 = ("com.example".g % "foo".a % "1.0.0" %> "1.1.0").single
    assertEquals(config.customLabelsFor(v2), List("foo-v2"))
    assertEquals(config.customLabelsFor(v1), List("example"))
  }

  test("matching override without customLabels is skipped in favor of the next matching entry") {
    val update = ("com.example".g % "frequency-only".a % "1.0.0" %> "1.1.0").single
    assertEquals(config.customLabelsFor(update), List("example"))
  }

  test("repo-wide labels are used if no matching override sets customLabels") {
    val frequencyOnlyConfig = RepoConfigAlg
      .parseRepoConfig(
        """|pullRequests.customLabels = [ "dependencies" ]
           |dependencyOverrides = [
           |  { dependency = { groupId = "com.example", artifactId = "foo" }, pullRequests = { frequency = "1 day" } },
           |  { dependency = { groupId = "com.example" }, pullRequests = {}, cooldown = { minimumAge = "2 days" } }
           |]
           |""".stripMargin
      )
      .fold(err => fail(s"Failed to parse config: $err"), identity)
    val update = ("com.example".g % "foo".a % "1.0.0" %> "1.1.0").single
    assertEquals(frequencyOnlyConfig.customLabelsFor(update), List("dependencies"))
  }

  test("override with empty customLabels removes all custom labels of a single update") {
    val update = ("com.example".g % "no-labels".a % "1.0.0" %> "1.1.0").single
    assertEquals(config.customLabelsFor(update), Nil)
  }

  test("artifact with empty customLabels override contributes no labels to a grouped update") {
    val noLabels = ("com.example".g % "no-labels".a % "1.0.0" %> "1.1.0").single
    val other = ("org.other".g % "lib".a % "1.0.0" %> "1.1.0").single
    val bar = ("com.example".g % "bar".a % "1.0.0" %> "1.1.0").single
    assertEquals(
      config.customLabelsFor(Update.Grouped("all", None, List(noLabels, other))),
      List("dependencies")
    )
    assertEquals(
      config.customLabelsFor(Update.Grouped("all", None, List(noLabels, bar))),
      List("example")
    )
  }

  test("grouped update gets no custom labels if all its artifacts have empty overrides") {
    val update = ("com.example".g % Nel.of("no-labels".a) % "1.0.0" %> "1.1.0").group
    assertEquals(config.customLabelsFor(update), Nil)
  }

  test("Update.ForGroupId uses override labels if all artifacts match the same entry") {
    val update = ("com.example".g % Nel.of("bar".a, "baz".a) % "1.0.0" %> "1.1.0").group
    assertEquals(config.customLabelsFor(update), List("example"))
  }

  test("Update.ForGroupId merges override labels with repo-wide labels of unmatched artifacts") {
    val update = ("org.other".g % Nel.of("lib".a, "lib-extra".a) % "1.0.0" %> "1.1.0").group
    val partialConfig = RepoConfigAlg
      .parseRepoConfig(
        """|pullRequests.customLabels = [ "dependencies" ]
           |dependencyOverrides = [
           |  { dependency = { groupId = "org.other", artifactId = "lib-extra" }, pullRequests = { customLabels = [ "extra" ] } }
           |]
           |""".stripMargin
      )
      .fold(err => fail(s"Failed to parse config: $err"), identity)
    assertEquals(partialConfig.customLabelsFor(update), List("dependencies", "extra"))
  }

  test("labels of artifacts that match different entries are merged") {
    // "bar" matches the groupId-wide entry, "foo" 2.x the more specific one
    val update = ("com.example".g % Nel.of("bar".a, "foo".a) % "1.0.0" %> "2.0.0").group
    assertEquals(config.customLabelsFor(update), List("example", "foo-v2"))
  }

  test("Update.Grouped merges labels of all its updates") {
    val bar = ("com.example".g % "bar".a % "1.0.0" %> "1.1.0").single
    val other = ("org.other".g % "lib".a % "1.0.0" %> "1.1.0").single
    val grouped = Update.Grouped("all", None, List(other, bar))
    assertEquals(config.customLabelsFor(grouped), List("dependencies", "example"))
  }

  test("Update.Grouped merges override labels of unrelated updates") {
    val groupedConfig = RepoConfigAlg
      .parseRepoConfig(
        """|pullRequests.customLabels = [ "dependencies" ]
           |dependencyOverrides = [
           |  { dependency = { groupId = "org.typelevel" }, pullRequests = { customLabels = [ "typelevel", "shared" ] } },
           |  { dependency = { groupId = "io.circe" }, pullRequests = { customLabels = [ "circe", "shared" ] } }
           |]
           |""".stripMargin
      )
      .fold(err => fail(s"Failed to parse config: $err"), identity)
    val cats = ("org.typelevel".g % "cats-core".a % "2.9.0" %> "2.10.0").single
    val circe = ("io.circe".g % "circe-core".a % "0.14.5" %> "0.14.6").single
    val grouped = Update.Grouped("all", None, List(cats, circe))
    assertEquals(groupedConfig.customLabelsFor(grouped), List("typelevel", "shared", "circe"))
  }

  test("Update.Grouped uses repo-wide labels once if none of its artifacts matches an entry") {
    val other = ("org.other".g % "lib".a % "1.0.0" %> "1.1.0").single
    val another = ("org.another".g % "lib".a % "1.0.0" %> "1.1.0").single
    val grouped = Update.Grouped("all", None, List(other, another))
    assertEquals(config.customLabelsFor(grouped), List("dependencies"))
  }
}
