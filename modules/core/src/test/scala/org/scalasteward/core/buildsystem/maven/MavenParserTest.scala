package org.scalasteward.core.buildsystem.maven

import org.scalasteward.core.TestSyntax._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MavenParserTest extends AnyFunSuite with Matchers {
  test("parseAllDependencies") {
    val input =
      """|[INFO] Scanning for projects...
         |[INFO]
         |[INFO] ----------------------< com.mycompany.app:my-app >----------------------
         |[INFO] Building my-app 1.0-SNAPSHOT
         |[INFO] --------------------------------[ jar ]---------------------------------
         |[INFO]
         |[INFO] --- maven-dependency-plugin:2.8:list (default-cli) @ my-app ---
         |[INFO]
         |[INFO] The following files have been resolved:
         |[INFO]    org.hamcrest:hamcrest-core:jar:1.3:test
         |[INFO]    junit:junit:jar:4.12:test
         |[INFO]    ch.qos.logback:logback-core:jar:1.1.11:compile
         |[INFO]
         |[INFO] ------------------------------------------------------------------------
         |[INFO] BUILD SUCCESS
         |[INFO] ------------------------------------------------------------------------
         |[INFO] Total time:  1.150 s
         |[INFO] Finished at: 2020-05-14T09:40:06+02:00
         |[INFO] ------------------------------------------------------------------------
         |""".stripMargin.linesIterator.toList
    val (_, dependencies) = MavenParser.parseAllDependencies(input)
    dependencies shouldBe List(
      "org.hamcrest" % "hamcrest-core" % "1.3" % "test",
      "junit" % "junit" % "4.12" % "test",
      "ch.qos.logback" % "logback-core" % "1.1.11"
    )
  }

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

  test("parse mvn dependency:list into dependencies") {

    val raw =
      """|[INFO]        id: sonatype-nexus-snapshots
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
         |""".stripMargin

    val x = MavenParser.parseResolvers(raw)

    print(x._2)
  }

}
