package org.scalasteward.core.maven

import cats.data.NonEmptyList
import org.scalasteward.core.data.{ArtifactId, CrossDependency, Dependency, GroupId, Update}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MavenParserTest extends AnyFunSuite with Matchers {

  val resolvers =
    """[INFO] Scanning for projects...
      |[WARNING] The project com.twilio:scala-service-maven-sample:pom:1.0-SNAPSHOT uses prerequisites which is only intended for maven-plugin projects but not for non maven-plugin projects. For such purposes you should use the maven-enforcer-plugin. See https://maven.apache.org/enforcer/enforcer-rules/requireMavenVersion.html
      |[WARNING] The project com.twilio:scala-service-maven-sample-client:jar:1.0-SNAPSHOT uses prerequisites which is only intended for maven-plugin projects but not for non maven-plugin projects. For such purposes you should use the maven-enforcer-plugin. See https://maven.apache.org/enforcer/enforcer-rules/requireMavenVersion.html
      |[WARNING] The project com.twilio:scala-service-maven-sample-server:jar:1.0-SNAPSHOT uses prerequisites which is only intended for maven-plugin projects but not for non maven-plugin projects. For such purposes you should use the maven-enforcer-plugin. See https://maven.apache.org/enforcer/enforcer-rules/requireMavenVersion.html
      |[INFO] ------------------------------------------------------------------------
      |[INFO] Reactor Build Order:
      |[INFO]
      |[INFO] scala-service-maven-sample                                         [pom]
      |[INFO] scala-service-maven-sample-client                                  [jar]
      |[INFO] scala-service-maven-sample-server                                  [jar]
      |[INFO]
      |[INFO] ---------------< com.twilio:scala-service-maven-sample >----------------
      |[INFO] Building scala-service-maven-sample 1.0-SNAPSHOT                   [1/3]
      |[INFO] --------------------------------[ pom ]---------------------------------
      |[INFO]
      |[INFO] --- maven-dependency-plugin:3.0.2:list-repositories (default-cli) @ scala-service-maven-sample ---
      |[INFO] Repositories used by this build:
      |[INFO]        id: sonatype-nexus-snapshots
      |      url: https://oss.sonatype.org/content/repositories/snapshots
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => false, update => daily]
      |
      |[INFO]        id: bintrayakkamaven
      |      url: https://dl.bintray.com/akka/maven/
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: apache.snapshots
      |      url: http://repository.apache.org/snapshots
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => false, update => daily]
      |
      |[INFO]        id: twilionexus
      |      url: https://nexus.corp.twilio.com/content/groups/public/
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: twilio.nexus
      |      url: https://nexus.corp.twilio.com/content/groups/public
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: central
      |      url: https://repo.maven.apache.org/maven2
      |   layout: default
      |snapshots: [enabled => false, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]
      |[INFO] ------------< com.twilio:scala-service-maven-sample-client >------------
      |[INFO] Building scala-service-maven-sample-client 1.0-SNAPSHOT            [2/3]
      |[INFO] --------------------------------[ jar ]---------------------------------
      |[INFO]
      |[INFO] --- maven-dependency-plugin:3.0.2:list-repositories (default-cli) @ scala-service-maven-sample-client ---
      |[INFO] Repositories used by this build:
      |[INFO]        id: sonatype-nexus-snapshots
      |      url: https://oss.sonatype.org/content/repositories/snapshots
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => false, update => daily]
      |
      |[INFO]        id: bintrayakkamaven
      |      url: https://dl.bintray.com/akka/maven/
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: apache.snapshots
      |      url: http://repository.apache.org/snapshots
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => false, update => daily]
      |
      |[INFO]        id: twilionexus
      |      url: https://nexus.corp.twilio.com/content/groups/public/
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: twilio.nexus
      |      url: https://nexus.corp.twilio.com/content/groups/public
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: central
      |      url: https://repo.maven.apache.org/maven2
      |   layout: default
      |snapshots: [enabled => false, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]
      |[INFO] ------------< com.twilio:scala-service-maven-sample-server >------------
      |[INFO] Building scala-service-maven-sample-server 1.0-SNAPSHOT            [3/3]
      |[INFO] --------------------------------[ jar ]---------------------------------
      |[INFO]
      |[INFO] --- maven-dependency-plugin:3.0.2:list-repositories (default-cli) @ scala-service-maven-sample-server ---
      |[INFO] Repositories used by this build:
      |[INFO]        id: sonatype-nexus-snapshots
      |      url: https://oss.sonatype.org/content/repositories/snapshots
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => false, update => daily]
      |
      |[INFO]        id: bintrayakkamaven
      |      url: https://dl.bintray.com/akka/maven/
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: apache.snapshots
      |      url: http://repository.apache.org/snapshots
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => false, update => daily]
      |
      |[INFO]        id: twilionexus
      |      url: https://nexus.corp.twilio.com/content/groups/public/
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: twilio.nexus
      |      url: https://nexus.corp.twilio.com/content/groups/public
      |   layout: default
      |snapshots: [enabled => true, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO]        id: central
      |      url: https://repo.maven.apache.org/maven2
      |   layout: default
      |snapshots: [enabled => false, update => daily]
      | releases: [enabled => true, update => daily]
      |
      |[INFO] ------------------------------------------------------------------------
      |[INFO] Reactor Summary for scala-service-maven-sample 1.0-SNAPSHOT:
      |[INFO]
      |[INFO] scala-service-maven-sample ......................... SUCCESS [  0.906 s]
      |[INFO] scala-service-maven-sample-client .................. SUCCESS [  0.033 s]
      |[INFO] scala-service-maven-sample-server .................. SUCCESS [  0.029 s]
      |[INFO] ------------------------------------------------------------------------
      |[INFO] BUILD SUCCESS
      |[INFO] ------------------------------------------------------------------------
      |[INFO] Total time:  1.161 s
      |[INFO] Finished at: 2020-03-07T16:37:38+02:00
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

//  test("parse `mvn versions:display-plugin-updates`") {
//    MavenAlg.parseUpdates(pluginUpdates) should contain allElementsOf List(
//      Update.Single(
//        CrossDependency(
//          Dependency(
//            GroupId("net.alchim31.maven"),
//            ArtifactId("scala-maven-plugin", None),
//            "3.3.2",
//            None,
//            None,
//            None
//          )
//        ),
//        NonEmptyList.one("4.3.0")
//      )
//    )
//  }

  test("parse mvn dependency:list into dependencies") {
    MavenAlg.parseDependencies(dependencies).headOption should contain(
      Dependency(
        GroupId("com.twilio"),
        ArtifactId("scala-service-http-server", "scala-service-http-server_2.12"),
        "0.52.0"
      )
    )
  }



}
