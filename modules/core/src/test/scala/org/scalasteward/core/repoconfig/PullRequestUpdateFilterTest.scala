package org.scalasteward.core.repoconfig

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*

class PullRequestUpdateFilterTest extends FunSuite {

  object Update {
    val kinesisClient =
      ("software.amazon.kinesis".g % "amazon-kinesis-client".a % "3.0.2" %> "3.0.3").single
    val s3Client = ("software.amazon.awssdk".g % "s3".a % "2.31.60" %> "2.31.61").single
    val play = ("org.playframework".g % "sbt-plugin".a % "3.0.6" %> "3.0.7").single
    val contentApiClient = ("com.gu".g % "content-api-client".a % "34.1.1" %> "8.1.5").single
    val playGoogleAuth = ("com.gu.play-googleauth".g % "play-v30".a % "23.0.0" %> "24.0.0").single
  }

  test("Allow a wildcard group id") {
    val groupFilter = PullRequestUpdateFilter(group = Some("*")).toOption.get

    assert(groupFilter.matches(Update.kinesisClient))
    assert(groupFilter.matches(Update.s3Client))
    assert(groupFilter.matches(Update.play))
  }

  test("Allow a wildcard-suffix on group id") {
    val groupFilter = PullRequestUpdateFilter(group = Some("software.amazon.*")).toOption.get

    assert(groupFilter.matches(Update.kinesisClient))
    assert(groupFilter.matches(Update.s3Client))

    assert(!groupFilter.matches(Update.play))
  }

  test("Not match a non-wildcard group id if it is only partially specified") {
    val groupFilter = PullRequestUpdateFilter(group = Some("software.amazon")).toOption.get

    assert(!groupFilter.matches(Update.kinesisClient))
    assert(!groupFilter.matches(Update.s3Client))
    assert(!groupFilter.matches(Update.play))
  }

  test("Not match a non-wildcard group id if it is only partially specified") {
    val groupFilter = PullRequestUpdateFilter(group = Some("com.gu.*")).toOption.get

    assert(!groupFilter.matches(Update.contentApiClient))
    assert(groupFilter.matches(Update.playGoogleAuth))
  }
}
