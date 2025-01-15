package org.scalasteward.core.data

import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.util.Nel

class UpdateCodecTest extends FunSuite {
  test("Decoder[ForArtifactId] V1") {
    val json = Json.obj(
      "Single" := Json.obj(
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
    val obtained = Decoder[Update].decodeJson(json)
    val expected = Right(("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single)
    assertEquals(obtained, expected)
  }

  test("Decoder[ForArtifactId] V2") {
    val json = Json.obj(
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
    val obtained = Decoder[Update].decodeJson(json)
    val expected = Right(("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single)
    assertEquals(obtained, expected)
  }

  test("Codec[ForArtifactId]") {
    val update = ("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single
    val obtained = Decoder[Update].decodeJson(Encoder[Update].apply(update))
    assertEquals(obtained, Right(update))
  }

  test("Decoder[ForGroupId] V1") {
    val json = Json.obj(
      "Group" := Json.obj(
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
    val obtained = Decoder[Update].decodeJson(json)
    val expected = Right(
      ("org.scala-sbt".g % Nel.of("sbt-launch".a, "scripted-plugin".a) % "1.2.1" %> "1.2.4").group
    )
    assertEquals(obtained, expected)
  }

  test("Decoder[ForGroupId] V2") {
    val json = Json.obj(
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
    val obtained = Decoder[Update].decodeJson(json)
    val expected = Right(
      ("org.scala-sbt".g % Nel.of("sbt-launch".a, "scripted-plugin".a) % "1.2.1" %> "1.2.4").group
    )
    assertEquals(obtained, expected)
  }

  test("Decoder[ForGroupId] V3") {
    val json = Json.obj(
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
    val obtained = Decoder[Update].decodeJson(json)
    val expected = Right(
      ("org.scala-sbt".g % Nel.of("sbt-launch".a, "scripted-plugin".a) % "1.2.1" %> "1.2.4").group
    )
    assertEquals(obtained, expected)
  }

  test("Codec[ForGroupId]") {
    val update =
      ("org.scala-sbt".g % Nel.of("sbt-launch".a, "scripted-plugin".a) % "1.2.1" %> "1.2.4").group
    val obtained = Decoder[Update].decodeJson(Encoder[Update].apply(update))
    assertEquals(obtained, Right(update))
  }

  test("Decoder[Grouped]") {
    val json = Json.obj(
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
    val obtained = Decoder[Update].decodeJson(json)
    val expected = Right(
      Update.Grouped(
        name = "all",
        title = Some("All"),
        updates = List(("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single)
      )
    )
    assertEquals(obtained, expected)
  }

  test("Codec[Grouped]") {
    val update = Update.Grouped(
      name = "all",
      title = Some("All"),
      updates = List(("org.specs2".g % "specs2-core".a % "3.9.4" % "test" %> "3.9.5").single)
    )
    val obtained = Decoder[Update].decodeJson(Encoder[Update].apply(update))
    assertEquals(obtained, Right(update))
  }
}
