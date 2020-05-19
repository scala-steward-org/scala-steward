package org.scalasteward.core.buildtool.maven

import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.ArtifactId
import org.scalasteward.core.data.Resolver.MavenRepository
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class parserTest extends AnyFunSuite with Matchers {
  test("parseDependencies") {
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
         |[INFO]    org.typelevel:cats-core_2.12:jar:1.5.0:compile
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
    val dependencies = parser.parseDependencies(input)
    dependencies shouldBe List(
      "org.typelevel" % ArtifactId("cats-core", "cats-core_2.12") % "1.5.0",
      "org.hamcrest" % "hamcrest-core" % "1.3" % "test",
      "junit" % "junit" % "4.12" % "test",
      "ch.qos.logback" % "logback-core" % "1.1.11"
    )
  }

  test("parseResolvers") {
    val input =
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
         |""".stripMargin.linesIterator.toList
    val resolvers = parser.parseResolvers(input)
    resolvers shouldBe List(
      MavenRepository(
        "sonatype-nexus-snapshots",
        "https://oss.sonatype.org/content/repositories/snapshots",
        None
      ),
      MavenRepository("bintrayakkamaven", "https://dl.bintray.com/akka/maven/", None),
      MavenRepository("apache.snapshots", "http://repository.apache.org/snapshots", None)
    )
  }
}
