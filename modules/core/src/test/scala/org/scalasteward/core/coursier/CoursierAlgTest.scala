package org.scalasteward.core.coursier

import munit.CatsEffectSuite
import org.http4s.syntax.literals.*
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.buildtool.sbt.data.{SbtVersion, ScalaVersion}
import org.scalasteward.core.data.Resolver
import org.scalasteward.core.mock.MockContext.context.coursierAlg
import org.scalasteward.core.mock.{MockEffOps, MockState}

class CoursierAlgTest extends CatsEffectSuite {
  private val resolvers = List(Resolver.mavenCentral)

  private val emptyMetadata = DependencyMetadata.empty

  test("getMetadata: with homePage and scmUrl") {
    val dep = "org.typelevel".g % ("cats-effect", "cats-effect_2.12").a % "1.0.0"
    val obtained = coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty)
    val expected = emptyMetadata.copy(
      homePage = Some(uri"https://typelevel.org/cats-effect/"),
      scmUrl = Some(uri"https://github.com/typelevel/cats-effect")
    )
    assertIO(obtained, expected)
  }

  test("getMetadata: homePage only") {
    val artifactId = ("play-ws-standalone-json", "play-ws-standalone-json_2.12").a
    val dep = "com.typesafe.play".g % artifactId % "2.1.0-M7"
    val obtained = coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty)
    val expected =
      emptyMetadata.copy(homePage = Some(uri"https://github.com/playframework/play-ws"))
    assertIO(obtained, expected)
  }

  test("getMetadata: scmUrl without scheme") {
    val dep = "org.msgpack".g % "msgpack-core".a % "0.8.20"
    val obtained = coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty)
    val expected = emptyMetadata.copy(homePage = Some(uri"http://msgpack.org/"))
    assertIO(obtained, expected)
  }

  test("getMetadata: scmUrl with git scheme") {
    val dep = "org.xhtmlrenderer".g % "flying-saucer-parent".a % "9.0.1"
    val obtained = coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty)
    val expected = emptyMetadata.copy(
      homePage = Some(uri"http://code.google.com/p/flying-saucer/"),
      scmUrl = Some(uri"git://github.com/flyingsaucerproject/flyingsaucer.git")
    )
    assertIO(obtained, expected)
  }

  test("getMetadata: homePage from parent") {
    val dep = "net.bytebuddy".g % "byte-buddy".a % "1.10.5"
    val obtained = coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty)
    val expected = emptyMetadata.copy(homePage = Some(uri"https://bytebuddy.net"))
    assertIO(obtained, expected)
  }

  test("getMetadata: minimal POM") {
    val dep = "altrmi".g % "altrmi-common".a % "0.9.6"
    val obtained = coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty)
    val expected = emptyMetadata
    assertIO(obtained, expected)
  }

  test("getMetadata: sbt plugin on Maven Central") {
    val dep = ("org.xerial.sbt".g % "sbt-sonatype".a % "3.8")
      .copy(sbtVersion = Some(SbtVersion("1.0")), scalaVersion = Some(ScalaVersion("2.12")))
    val obtained = coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty)
    val expected = emptyMetadata.copy(
      homePage = Some(uri"https://github.com/xerial/sbt-sonatype"),
      scmUrl = Some(uri"https://github.com/xerial/sbt-sonatype")
    )
    assertIO(obtained, expected)
  }

  test("getMetadata: sbt plugin on sbt-plugin-releases") {
    val dep = ("com.github.gseitz".g % "sbt-release".a % "1.0.12")
      .copy(sbtVersion = Some(SbtVersion("1.0")), scalaVersion = Some(ScalaVersion("2.12")))
    val obtained = coursierAlg.getMetadata(dep, List(sbtPluginReleases)).runA(MockState.empty)
    val expected = emptyMetadata.copy(homePage = Some(uri"https://github.com/sbt/sbt-release"))
    assertIO(obtained, expected)
  }

  test("getMetadata: scmUrl with github scheme") {
    val dep = "com.github.japgolly.scalajs-react".g % ("core", "core_sjs1_2.13").a % "2.0.0-RC5"
    val obtained = coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty)
    val expected = emptyMetadata.copy(
      homePage = Some(uri"https://github.com/japgolly/scalajs-react"),
      scmUrl = Some(uri"github.com:japgolly/scalajs-react.git"),
      versionScheme = Some("early-semver")
    )
    assertIO(obtained, expected)
  }

  test("getMetadata: no resolvers") {
    val dep = "org.example".g % "foo".a % "1.0.0"
    val obtained = coursierAlg.getMetadata(dep, List.empty).runA(MockState.empty)
    val expected = emptyMetadata
    assertIO(obtained, expected)
  }

  test("getMetadata: resolver with headers") {
    val dep = "org.typelevel".g % ("cats-effect", "cats-effect_2.12").a % "1.0.0"
    val resolvers =
      List(Resolver.mavenCentral.copy(headers = Some(List(Resolver.Header("X-Foo", "bar")))))
    val obtained =
      coursierAlg.getMetadata(dep, resolvers).runA(MockState.empty).map(_.repoUrl.isDefined)
    assertIOBoolean(obtained)
  }
}
