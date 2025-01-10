package org.scalasteward.core.repoconfig

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*

class RetractedArtifactTest extends FunSuite {
  private val retractedArtifact = RetractedArtifact(
    "a reason",
    "doc URI",
    List(
      UpdatePattern(
        "org.portable-scala".g,
        Some("sbt-scalajs-crossproject"),
        Some(VersionPattern(exact = Some("1.0.0")))
      )
    )
  )

  test("isRetracted") {
    val update = ("org.portable-scala".g % "sbt-scalajs-crossproject".a % "0.9.2" %> "1.0.0").single
    assert(retractedArtifact.isRetracted(update))
  }

  test("not isRetracted") {
    val update = ("org.portable-scala".g % "sbt-scalajs-crossproject".a % "0.9.2" %> "0.9.3").single
    assert(!retractedArtifact.isRetracted(update))
  }
}
