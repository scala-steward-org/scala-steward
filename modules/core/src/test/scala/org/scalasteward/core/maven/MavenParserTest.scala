package org.scalasteward.core.maven

import cats.data.NonEmptyList
import org.scalasteward.core.data.{ArtifactId, CrossDependency, Dependency, GroupId, Update}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MavenParserTest extends AnyFunSuite with Matchers {

  val pluginUpdates: List[String] =
    """[INFO] Scanning for projects...
      |[WARNING] The project com.twilio:scala-service-maven-sample:pom:1.0-SNAPSHOT uses prerequisites which is only intended for maven-plugin projects but not for non maven-plugin projects. For such purposes you should use the maven-enforcer-plugin. See https://maven.apache.org/enforcer/enforcer-rules/requireMavenVersion.html
      |[WARNING] The project com.twilio:scala-service-maven-sample-client:jar:1.0-SNAPSHOT uses prerequisites which is only intended for maven-plugin projects but not for non maven-plugin projects. For such purposes you should use the maven-enforcer-plugin. See https://maven.apache.org/enforcer/enforcer-rules/requireMavenVersion.html
      |[INFO] ------------------------------------------------------------------------
      |[INFO] Reactor Build Order:
      |[INFO]
      |[INFO] scala-service-maven-sample                                         [pom]
      |[INFO] scala-service-maven-sample-client                                  [jar]
      |[INFO]
      |[INFO] ---------------< com.twilio:scala-service-maven-sample >----------------
      |[INFO] Building scala-service-maven-sample 1.0-SNAPSHOT                   [1/2]
      |[INFO] --------------------------------[ pom ]---------------------------------
      |[INFO]
      |[INFO] --- versions-maven-plugin:2.7:display-plugin-updates (default-cli) @ scala-service-maven-sample ---
      |[INFO]
      |[INFO] The following plugin updates are available:
      |[INFO]   com.twilio.maven.plugins:guardrail-twilio-maven-plugin  0.53.1 -> 4.jsterlinginstanttest
      |[INFO]   net.alchim31.maven:scala-maven-plugin .............. 3.3.2 -> 4.3.0
      |[INFO]
      |[WARNING] The following plugins do not have their version specified:
      |[WARNING]   maven-clean-plugin ...................... (from super-pom) 3.1.0
      |[WARNING]   maven-site-plugin ....................... (from super-pom) 3.8.2
      |[INFO]
      |[INFO] Project defines minimum Maven version as: 3.3.9
      |[INFO] Plugins require minimum Maven version of: 3.0.5
      |[INFO] Note: the super-pom from Maven 3.6.2 defines some of the plugin
      |[INFO]       versions and may be influencing the plugins required minimum Maven
      |[INFO]       version.
      |[INFO]
      |[INFO] No plugins require a newer version of Maven than specified by the pom.
      |[INFO]
      |[INFO]
      |[INFO] ------------< com.twilio:scala-service-maven-sample-client >------------
      |[INFO] Building scala-service-maven-sample-client 1.0-SNAPSHOT            [2/2]
      |[INFO] --------------------------------[ jar ]---------------------------------
      |[INFO]
      |[INFO] --- versions-maven-plugin:2.7:display-plugin-updates (default-cli) @ scala-service-maven-sample-client ---
      |[INFO]
      |[INFO] All plugins with a version specified are using the latest versions.
      |[INFO]
      |[WARNING] The following plugins do not have their version specified:
      |[WARNING]   maven-clean-plugin ...................... (from super-pom) 3.1.0
      |[WARNING]   maven-site-plugin ....................... (from super-pom) 3.8.2
      |[INFO]
      |[INFO] Project defines minimum Maven version as: 3.3.9
      |[INFO] Plugins require minimum Maven version of: 3.0.5
      |[INFO] Note: the super-pom from Maven 3.6.2 defines some of the plugin
      |[INFO]       versions and may be influencing the plugins required minimum Maven
      |[INFO]       version.
      |[INFO]
      |[INFO] No plugins require a newer version of Maven than specified by the pom.
      |[INFO]
      |[INFO] ------------------------------------------------------------------------
      |[INFO] Reactor Summary for scala-service-maven-sample 1.0-SNAPSHOT:
      |[INFO]
      |[INFO] scala-service-maven-sample ......................... SUCCESS [  0.648 s]
      |[INFO] scala-service-maven-sample-client .................. SUCCESS [  0.051 s]
      |[INFO] ------------------------------------------------------------------------
      |[INFO] BUILD SUCCESS
      |[INFO] ------------------------------------------------------------------------
      |[INFO] Total time:  0.915 s
      |[INFO] Finished at: 2019-12-12T16:04:00+02:00
      |[INFO] ------------------------------------------------------------------------""".stripMargin.stripMargin.linesIterator.toList

  val updates: List[String] =
    """[INFO] Scanning for projects...
      |[WARNING] The project com.twilio:scala-service-maven-sample:jar:1.0-SNAPSHOT uses prerequisites which is only intended for maven-plugin projects but not for non maven-plugin projects. For such purposes you should use the maven-enforcer-plugin. See https://maven.apache.org/enforcer/enforcer-rules/requireMavenVersion.html
      |[INFO]
      |[INFO] ---------------< com.twilio:scala-service-maven-sample >----------------
      |[INFO] Building scala-service-maven-sample 1.0-SNAPSHOT
      |[INFO] --------------------------------[ jar ]---------------------------------
      |[INFO]
      |[INFO] --- versions-maven-plugin:2.7:display-dependency-updates (default-cli) @ scala-service-maven-sample ---
      |[INFO] The following dependencies in Dependency Management have newer versions:
      |[INFO]   io.kamon:kamon-core_2.12 .............................. 0.6.5 -> 2.0.2
      |[INFO]   io.kamon:kamon-datadog_2.12 ........................... 0.6.5 -> 2.0.1
      |[INFO]   io.kamon:kamon-system-metrics_2.12 .................... 0.6.5 -> 2.0.1
      |[INFO]   org.scala-lang:scala-library ........................ 2.12.6 -> 2.13.1
      |[INFO]   org.scalatest:scalatest_2.12 ................... 3.0.0 -> 3.2.0-SNAP10
      |[INFO]
      |[INFO] ------------------------------------------------------------------------
      |[INFO] BUILD SUCCESS
      |[INFO] ------------------------------------------------------------------------
      |[INFO] Total time:  1.180 s
      |[INFO] Finished at: 2019-12-11T11:23:38+02:00
      |[INFO] ------------------------------------------------------------------------""".stripMargin.linesIterator.toList

  val dependencies =
    """
      |[INFO] Scanning for projects...
      |Downloading from twilio.nexus: https://nexus.corp.twilio.com/content/groups/public/com/twilio/twilio-parent-pom/8.2.92/twilio-parent-pom-8.2.92.pom
      |Downloaded from twilio.nexus: https://nexus.corp.twilio.com/content/groups/public/com/twilio/twilio-parent-pom/8.2.92/twilio-parent-pom-8.2.92.pom (38 kB at
      |41 kB/s)
      |[WARNING] The project com.twilio:scala-service-maven-sample:jar:1.0-SNAPSHOT uses prerequisites which is only intended for maven-plugin projects but not for
      |non maven-plugin projects. For such purposes you should use the maven-enforcer-plugin. See https://maven.apache.org/enforcer/enforcer-rules/requireMavenVersi
      |on.html
      |[INFO]
      |[INFO] ---------------< com.twilio:scala-service-maven-sample >----------------
      |[INFO] Building scala-service-maven-sample 1.0-SNAPSHOT
      |[INFO] --------------------------------[ jar ]---------------------------------
      |[INFO] The following files have been resolved:
      |[INFO]    com.twilio:scala-service-http-server_2.12:jar:0.52.0:compile
      |[INFO]    org.scala-lang:scala-library:jar:2.12.6:compile
      [INFO]    com.twilio:scala-service-json_2.12:jar:0.52.0:compile
      [INFO]    io.circe:circe-java8_2.12:jar:0.11.1:compile
      [INFO]    com.twilio:scala-service-headers_2.12:jar:0.52.0:compile
      [INFO]    com.chuusai:shapeless_2.12:jar:2.3.3:compile
      [INFO]    org.typelevel:macro-compat_2.12:jar:1.1.1:compile
      [INFO]    com.typesafe.akka:akka-slf4j_2.12:jar:2.5.22:compile
      [INFO]    com.typesafe:config:jar:1.3.4:compile
      [INFO]    com.twilio:twilio-rollbar-logback:jar:4.2.1:compile
      [INFO]    ch.qos.logback:logback-classic:jar:1.1.11:compile
      [INFO]    ch.qos.logback:logback-core:jar:1.1.11:compile
      [INFO]    net.java.dev.jna:jna:jar:4.0.0:compile
      [INFO]    com.twilio:scala-service-http-client_2.12:jar:0.52.0:compile
      [INFO]    com.twilio:coreutil-sids:jar:9.4.33:compile
      [INFO]    commons-codec:commons-codec:jar:1.8:compile
      [INFO]    org.typelevel:cats-core_2.12:jar:1.5.0:compile
      [INFO]    org.typelevel:cats-macros_2.12:jar:1.5.0:compile
      [INFO]    org.typelevel:cats-kernel_2.12:jar:1.5.0:compile
      [INFO]    org.typelevel:machinist_2.12:jar:0.6.6:compile
      [INFO]    org.slf4j:slf4j-api:jar:1.7.25:compile
      [INFO]    com.typesafe.akka:akka-actor_2.12:jar:2.5.22:compile
      [INFO]    org.scala-lang.modules:scala-java8-compat_2.12:jar:0.8.0:compile
      [INFO]    com.typesafe.akka:akka-stream_2.12:jar:2.5.22:compile
      [INFO]    com.typesafe.akka:akka-protobuf_2.12:jar:2.5.22:compile
      [INFO]    org.reactivestreams:reactive-streams:jar:1.0.2:compile
      [INFO]    com.typesafe:ssl-config-core_2.12:jar:0.3.7:compile
      [INFO]    com.typesafe.akka:akka-http-core_2.12:jar:10.1.8:compile
      [INFO]    com.typesafe.akka:akka-parsing_2.12:jar:10.1.8:compile
      [INFO]    com.typesafe.akka:akka-http_2.12:jar:10.1.8:compile
      [INFO]    io.circe:circe-core_2.12:jar:0.11.1:compile
      [INFO]    io.circe:circe-numbers_2.12:jar:0.11.1:compile
      |""".stripMargin.linesIterator.toList

  test("parse `mvn versions:display-plugin-updates`") {
    MavenAlg.parseUpdates(pluginUpdates) shouldBe List(
      Update.Single(
        CrossDependency(
          Dependency(
            GroupId("com.twilio.maven.plugins"),
            ArtifactId("guardrail-twilio-maven-plugin", "guardrail-twilio-maven-plugin"),
            "0.53.1",
            None,
            None,
            None
          )
        ),
        NonEmptyList.one("4.jsterlinginstanttest")
      ),
      Update.Single(
        CrossDependency(
          Dependency(
            GroupId("net.alchim31.maven"),
            ArtifactId("scala-maven-plugin", "scala-maven-plugin"),
            "3.3.2",
            None,
            None,
            None
          )
        ),
        NonEmptyList.one("4.3.0")
      )
    )
  }

  test("parse mvn dependency:list into dependencies") {
    MavenAlg.parseDependencies(dependencies).headOption should contain(
      Dependency(
        GroupId("com.twilio"),
        ArtifactId("scala-service-http-server", "scala-service-http-server_2.12"),
        "0.52.0"
      )
    )
  }

  test("parse mvn display-dependency-updates into updates") {
    MavenAlg.parseUpdates(updates).headOption should contain(
      Update.Single(
        CrossDependency(
          Dependency(
            GroupId("io.kamon"),
            ArtifactId("kamon-core", "kamon-core_2.12"),
            "0.6.5",
            sbtVersion = None,
            scalaVersion = None,
            configurations = None
          )),
        NonEmptyList.one("2.0.2")
      )
    )

    //todo: configuration (test, provided) is missing
  }

}
