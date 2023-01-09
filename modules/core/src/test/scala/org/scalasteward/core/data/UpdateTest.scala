package org.scalasteward.core.data

import io.circe.Json
import io.circe.syntax._
import munit.FunSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.util.Nel

class UpdateTest extends FunSuite {
  test("Group.mainArtifactId") {
    val update = ("org.http4s".g %
      Nel.of("http4s-blaze-server".a, "http4s-circe".a, "http4s-core".a, "http4s-dsl".a) %
      "0.18.16" %> "0.18.18").group
    assertEquals(update.mainArtifactId, "http4s-core")
  }

  test("Group.mainArtifactId: artifactIds contains a common suffix") {
    val update = ("com.softwaremill.sttp".g %
      Nel.of("circe".a, "core".a, "monix".a) % "1.3.2" %> "1.3.3").group
    assertEquals(update.mainArtifactId, "circe")
  }

  test("groupByGroupId: 1 update") {
    val updates = List(("org.specs2".g % "specs2-core".a % "3.9.4" %> "3.9.5").single)
    assertEquals(Update.groupByGroupId(updates), updates)
  }

  test("groupByGroupId: 2 updates") {
    val update0 = ("org.specs2".g % "specs2-core".a % "3.9.4" %> "3.9.5").single
    val update1 = ("org.specs2".g % "specs2-scalacheck".a % "3.9.4" %> "3.9.5").single
    val expected = List(
      ("org.specs2".g % Nel.of("specs2-core".a, "specs2-scalacheck".a) % "3.9.4" %> "3.9.5").group
    )
    assertEquals(Update.groupByGroupId(List(update0, update1)), expected)
  }

  test("groupByArtifactIdName: 2 updates") {
    val update0 =
      ("org.specs2".g % ("specs2-core", "specs2-core_2.12").a % "3.9.4" %> "3.9.5").single
    val update1 =
      ("org.specs2".g % ("specs2-core", "specs2-core_2.13").a % "3.9.4" % "test" %> "3.9.5").single
    val expected = List(
      ((update0.crossDependency.dependencies ::: update1.crossDependency.dependencies) -> "3.9.5").single
    )
    assertEquals(Update.groupByArtifactIdName(List(update0, update1)), expected)
  }

  test("groupByArtifactIdName: 3 updates ") {
    val update0 = ("org.specs2".g % "specs2-core".a % "3.9.4" %> "3.9.5").single
    val update1 = ("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single
    val update2 = ("org.specs2".g % "specs2-scalacheck".a % "3.9.4" %> "3.9.5").single
    val expected = List(
      (Nel.of(
        "org.specs2".g % "specs2-core".a % "3.9.4",
        "org.specs2".g % "specs2-core".a % "3.9.4" % "test"
      ) %> "3.9.5").single,
      update2
    )
    assertEquals(Update.groupByArtifactIdName(List(update0, update1, update2)), expected)
  }

  test("Update.show") {
    val update = ("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single
    assertEquals(update.show, "org.specs2:specs2-core : 3.9.4 -> 3.9.5")
  }

  test("Group.show") {
    val update =
      ("org.scala-sbt".g % Nel.of("sbt-launch".a, "scripted-plugin".a) % "1.2.1" %> "1.2.4").group
    assertEquals(update.show, "org.scala-sbt:{sbt-launch, scripted-plugin} : 1.2.1 -> 1.2.4")
  }

  test("ForArtifactId Encoder/Decoder") {
    val update1: Update =
      ("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single
    val update2: Update.ForArtifactId =
      ("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single

    val expected = Json.obj(
      "ForArtifactId" := Json.obj(
        "crossDependency" := Json.arr(
          Json.obj(
            "groupId" := "org.specs2",
            "artifactId" := Json.obj(
              "name" := "specs2-core",
              "maybeCrossName" := None
            ),
            "version" := "3.9.4",
            "sbtVersion" := None,
            "scalaVersion" := None,
            "configurations" := "test"
          )
        ),
        "newerVersions" := List("3.9.5"),
        "newerGroupId" := None,
        "newerArtifactId" := None
      )
    )

    assertEquals(update1.asJson, expected)
    assertEquals(update1.asJson, update2.asJson)
    assertEquals(update1.asJson.as[Update], update2.asJson.as[Update])
    assertEquals(update1.asJson.as[Update.ForArtifactId], update2.asJson.as[Update.ForArtifactId])
    assertEquals(update1.asJson.as[Update], update2.asJson.as[Update.ForArtifactId])
  }

  test("ForGroupId Encoder/Decoder") {
    val update1: Update =
      ("org.scala-sbt".g % Nel.of("sbt-launch".a, "scripted-plugin".a) % "1.2.1" %> "1.2.4").group
    val update2: Update.ForGroupId =
      ("org.scala-sbt".g % Nel.of("sbt-launch".a, "scripted-plugin".a) % "1.2.1" %> "1.2.4").group

    val oldJson = Json.obj(
      "ForGroupId" := Json.obj(
        "crossDependencies" := Json.arr(
          Json.arr(
            Json.obj(
              "groupId" := "org.scala-sbt",
              "artifactId" := Json.obj(
                "name" := "sbt-launch",
                "maybeCrossName" := None
              ),
              "version" := "1.2.1",
              "sbtVersion" := None,
              "scalaVersion" := None,
              "configurations" := None
            )
          ),
          Json.arr(
            Json.obj(
              "groupId" := "org.scala-sbt",
              "artifactId" := Json.obj(
                "name" := "scripted-plugin",
                "maybeCrossName" := None
              ),
              "version" := "1.2.1",
              "sbtVersion" := None,
              "scalaVersion" := None,
              "configurations" := None
            )
          )
        ),
        "newerVersions" := List("1.2.4")
      )
    )

    val newJson = Json.obj(
      "ForGroupId" := Json.obj(
        "forArtifactIds" := Json.arr(
          Json.obj(
            "ForArtifactId" := Json.obj(
              "crossDependency" := Json.arr(
                Json.obj(
                  "groupId" := "org.scala-sbt",
                  "artifactId" := Json.obj(
                    "name" := "sbt-launch",
                    "maybeCrossName" := None
                  ),
                  "version" := "1.2.1",
                  "sbtVersion" := None,
                  "scalaVersion" := None,
                  "configurations" := None
                )
              ),
              "newerVersions" := List("1.2.4"),
              "newerGroupId" := None,
              "newerArtifactId" := None
            )
          ),
          Json.obj(
            "ForArtifactId" := Json.obj(
              "crossDependency" := Json.arr(
                Json.obj(
                  "groupId" := "org.scala-sbt",
                  "artifactId" := Json.obj(
                    "name" := "scripted-plugin",
                    "maybeCrossName" := None
                  ),
                  "version" := "1.2.1",
                  "sbtVersion" := None,
                  "scalaVersion" := None,
                  "configurations" := None
                )
              ),
              "newerVersions" := List("1.2.4"),
              "newerGroupId" := None,
              "newerArtifactId" := None
            )
          )
        )
      )
    )

    assertEquals(oldJson.as[Update.ForGroupId], Right(update2))
    assertEquals(newJson.as[Update.ForGroupId], Right(update2))
    assertEquals(update1.asJson, newJson)
    assertEquals(update1.asJson, update2.asJson)
    assertEquals(update1.asJson.as[Update], update2.asJson.as[Update])
    assertEquals(update1.asJson.as[Update.ForGroupId], update2.asJson.as[Update.ForGroupId])
    assertEquals(update1.asJson.as[Update], update2.asJson.as[Update.ForGroupId])
  }

  test("Grouped Encoder/Decoder") {
    val update1: Update =
      Update.Grouped(
        name = "all",
        title = Some("All"),
        updates = List(("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single)
      )
    val update2: Update.Grouped =
      Update.Grouped(
        name = "all",
        title = Some("All"),
        updates = List(("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single)
      )

    val expected = Json.obj(
      "Grouped" := Json.obj(
        "name" := "all",
        "title" := "All",
        "updates" := Json.arr(
          Json.obj(
            "ForArtifactId" := Json.obj(
              "crossDependency" := Json.arr(
                Json.obj(
                  "groupId" := "org.specs2",
                  "artifactId" := Json.obj(
                    "name" := "specs2-core",
                    "maybeCrossName" := None
                  ),
                  "version" := "3.9.4",
                  "sbtVersion" := None,
                  "scalaVersion" := None,
                  "configurations" := "test"
                )
              ),
              "newerVersions" := List("3.9.5"),
              "newerGroupId" := None,
              "newerArtifactId" := None
            )
          )
        )
      )
    )

    assertEquals(update1.asJson, expected)
    assertEquals(update1.asJson, update2.asJson)
    assertEquals(update1.asJson.as[Update], update2.asJson.as[Update])
    assertEquals(update1.asJson.as[Update.Grouped], update2.asJson.as[Update.Grouped])
    assertEquals(update1.asJson.as[Update], update2.asJson.as[Update.Grouped])
  }
}
