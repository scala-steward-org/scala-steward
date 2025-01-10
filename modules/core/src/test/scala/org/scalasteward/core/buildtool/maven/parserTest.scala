package org.scalasteward.core.buildtool.maven

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.Resolver.MavenRepository

class parserTest extends FunSuite {
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
    val expected = List(
      "org.typelevel".g % ("cats-core", "cats-core_2.12").a % "1.5.0",
      "org.hamcrest".g % "hamcrest-core".a % "1.3" % "test",
      "junit".g % "junit".a % "4.12" % "test",
      "ch.qos.logback".g % "logback-core".a % "1.1.11"
    )
    assertEquals(dependencies, expected)
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
    val expected = List(
      MavenRepository(
        "sonatype-nexus-snapshots",
        "https://oss.sonatype.org/content/repositories/snapshots",
        None,
        None
      ),
      MavenRepository("bintrayakkamaven", "https://dl.bintray.com/akka/maven/", None, None),
      MavenRepository("apache.snapshots", "http://repository.apache.org/snapshots", None, None)
    )
    assertEquals(resolvers, expected)
  }
}
