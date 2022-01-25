package org.scalasteward.core.coursier

import munit.CatsEffectSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.data.{SbtVersion, ScalaVersion}
import org.scalasteward.core.mock.MockContext.context.coursierAlg
import org.scalasteward.core.mock.MockState

class CoursierAlgTest extends CatsEffectSuite {
  test("getArtifactUrl: library") {
    val dep = "org.typelevel".g % ("cats-effect", "cats-effect_2.12").a % "1.0.0"
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/typelevel/cats-effect"))
    }
  }

  test("getArtifactUrl: defaults to homepage") {
    val artifactId = ("play-ws-standalone-json", "play-ws-standalone-json_2.12").a
    val dep = "com.typesafe.play".g % artifactId % "2.1.0-M7"
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/playframework/play-ws"))
    }
  }

  test("getArtifactUrl: URL with no or invalid scheme 1") {
    val dep = "org.msgpack".g % "msgpack-core".a % "0.8.20"
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"http://msgpack.org/"))
    }
  }

  test("getArtifactUrl: URL with no or invalid scheme 2") {
    val dep = "org.xhtmlrenderer".g % "flying-saucer-parent".a % "9.0.1"
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"http://code.google.com/p/flying-saucer/"))
    }
  }

  test("getArtifactUrl: from parent") {
    val dep = "net.bytebuddy".g % "byte-buddy".a % "1.10.5"
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://bytebuddy.net"))
    }
  }

  test("getArtifactUrl: minimal pom") {
    val dep = "altrmi".g % "altrmi-common".a % "0.9.6"
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, None)
    }
  }

  test("getArtifactUrl: sbt plugin on Maven Central") {
    val dep = ("org.xerial.sbt".g % "sbt-sonatype".a % "3.8")
      .copy(sbtVersion = Some(SbtVersion("1.0")), scalaVersion = Some(ScalaVersion("2.12")))
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/xerial/sbt-sonatype"))
    }
  }

  test("getArtifactUrl: sbt plugin on sbt-plugin-releases") {
    val dep = ("com.github.gseitz".g % "sbt-release".a % "1.0.12")
      .copy(sbtVersion = Some(SbtVersion("1.0")), scalaVersion = Some(ScalaVersion("2.12")))
    coursierAlg.getArtifactUrl(dep.withSbtPluginReleases).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/sbt/sbt-release"))
    }
  }

  test("getArtifactUrl: invalid scm URL but valid homepage") {
    val dep = "com.github.japgolly.scalajs-react".g % ("core", "core_sjs1_2.13").a % "2.0.0-RC5"
    coursierAlg.getArtifactUrl(dep.withMavenCentral).runA(MockState.empty).map { obtained =>
      assertEquals(obtained, Some(uri"https://github.com/japgolly/scalajs-react"))
    }
  }

  test("getArtifactIdUrlMapping") {
    val deps = List(
      "org.typelevel".g % ("cats-core", "cats-core_2.12").a % "1.6.0",
      "org.typelevel".g % ("cats-effect", "cats-effect_2.12").a % "1.0.0"
    )
    coursierAlg.getArtifactIdUrlMapping(deps.withMavenCentral).runA(MockState.empty).map {
      obtained =>
        val expected = Map(
          "cats-core" -> uri"https://github.com/typelevel/cats",
          "cats-effect" -> uri"https://github.com/typelevel/cats-effect"
        )
        assertEquals(obtained, expected)
    }
  }
}
