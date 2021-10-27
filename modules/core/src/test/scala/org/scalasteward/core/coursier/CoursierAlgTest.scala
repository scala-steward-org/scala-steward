package org.scalasteward.core.coursier

import munit.CatsEffectSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.data.{SbtVersion, ScalaVersion}
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId}
import org.scalasteward.core.mock.MockContext.context.coursierAlg
import org.scalasteward.core.mock.MockState

class CoursierAlgTest extends CatsEffectSuite {
  test("getArtifactUrl: library") {
    val artifactId = ArtifactId("cats-effect", "cats-effect_2.12")
    val dep = Dependency(GroupId("org.typelevel"), artifactId, "1.0.0")
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/typelevel/cats-effect"))
    }
  }

  test("getArtifactUrl: defaults to homepage") {
    val artifactId = ArtifactId("play-ws-standalone-json", "play-ws-standalone-json_2.12")
    val dep = Dependency(GroupId("com.typesafe.play"), artifactId, "2.1.0-M7")
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/playframework/play-ws"))
    }
  }

  test("getArtifactUrl: URL with no or invalid scheme 1") {
    val dep = Dependency(GroupId("org.msgpack"), ArtifactId("msgpack-core"), "0.8.20")
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"http://msgpack.org/"))
    }
  }

  test("getArtifactUrl: URL with no or invalid scheme 2") {
    val dep = Dependency(GroupId("org.xhtmlrenderer"), ArtifactId("flying-saucer-parent"), "9.0.1")
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"http://code.google.com/p/flying-saucer/"))
    }
  }

  test("getArtifactUrl: from parent") {
    val dep = Dependency(GroupId("net.bytebuddy"), ArtifactId("byte-buddy"), "1.10.5")
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://bytebuddy.net"))
    }
  }

  test("getArtifactUrl: minimal pom") {
    val dep = Dependency(GroupId("altrmi"), ArtifactId("altrmi-common"), "0.9.6")
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, None)
    }
  }

  test("getArtifactUrl: sbt plugin on Maven Central") {
    val dep = Dependency(
      GroupId("org.xerial.sbt"),
      ArtifactId("sbt-sonatype"),
      "3.8",
      Some(SbtVersion("1.0")),
      Some(ScalaVersion("2.12"))
    )
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/xerial/sbt-sonatype"))
    }
  }

  test("getArtifactUrl: sbt plugin on sbt-plugin-releases") {
    val dep = Dependency(
      GroupId("com.github.gseitz"),
      ArtifactId("sbt-release"),
      "1.0.12",
      Some(SbtVersion("1.0")),
      Some(ScalaVersion("2.12"))
    )
    coursierAlg.getArtifactUrl(dep.withSbtPluginReleases).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/sbt/sbt-release"))
    }
  }

  test("getArtifactUrl: invalid scm URL but valid homepage") {
    val groupId = GroupId("com.github.japgolly.scalajs-react")
    val dep = Dependency(groupId, ArtifactId("core", "core_sjs1_2.13"), "2.0.0-RC5")

    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/japgolly/scalajs-react"))
    }
  }

  test("getArtifactIdUrlMapping") {
    val dependencies = List(
      Dependency(GroupId("org.typelevel"), ArtifactId("cats-core", "cats-core_2.12"), "1.6.0"),
      Dependency(GroupId("org.typelevel"), ArtifactId("cats-effect", "cats-effect_2.12"), "1.0.0")
    )
    coursierAlg.getArtifactIdUrlMapping(dependencies.withMavenCentral).runA(MockState.empty).map {
      obtained =>
        val expected = Map(
          "cats-core" -> uri"https://github.com/typelevel/cats",
          "cats-effect" -> uri"https://github.com/typelevel/cats-effect"
        )
        assertEquals(obtained, expected)
    }
  }
}
