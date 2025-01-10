package org.scalasteward.core.buildtool.mill

import munit.FunSuite
import org.scalasteward.core.TestSyntax.*
import org.scalasteward.core.data.Resolver

class MillDepParserTest extends FunSuite {
  test("parse dependencies from https://github.com/lihaoyi/requests-scala") {
    val data =
      """{
        |  "modules": [
        |    {
        |      "name": "requests[2.12.6]",
        |      "repositories": [
        |        {
        |          "url": "https://repo1.maven.org/maven2",
        |          "type": "maven",
        |          "auth": null
        |        },
        |        {
        |          "url": "https://oss.sonatype.org/content/repositories/releases",
        |          "type": "maven",
        |          "auth": null
        |        }
        |      ],
        |      "dependencies": [
        |        {
        |          "groupId": "com.lihaoyi",
        |          "artifactId": {
        |            "name": "geny",
        |            "maybeCrossName": "geny_2.12"
        |          },
        |          "version": "0.6.0"
        |        }
        |      ]
        |    },
        |    {
        |      "name": "requests[2.12.6].test",
        |      "repositories": [
        |        {
        |          "url": "https://repo1.maven.org/maven2",
        |          "type": "maven",
        |          "auth": null
        |        },
        |        {
        |          "url": "https://oss.sonatype.org/content/repositories/releases",
        |          "type": "maven",
        |          "auth": null
        |        }
        |      ],
        |      "dependencies": [
        |        {
        |          "groupId": "com.lihaoyi",
        |          "artifactId": {
        |            "name": "utest",
        |            "maybeCrossName": "utest_2.12"
        |          },
        |          "version": "0.7.3"
        |        },
        |        {
        |          "groupId": "com.lihaoyi",
        |          "artifactId": {
        |            "name": "ujson",
        |            "maybeCrossName": "ujson_2.12"
        |          },
        |          "version": "1.1.0"
        |        }
        |      ]
        |    },
        |    {
        |      "name": "requests[2.13.0]",
        |      "repositories": [
        |        {
        |          "url": "https://repo1.maven.org/maven2",
        |          "type": "maven",
        |          "auth": null
        |        },
        |        {
        |          "url": "https://oss.sonatype.org/content/repositories/releases",
        |          "type": "maven",
        |          "auth": null
        |        }
        |      ],
        |      "dependencies": [
        |        {
        |          "groupId": "com.lihaoyi",
        |          "artifactId": {
        |            "name": "geny",
        |            "maybeCrossName": "geny_2.13"
        |          },
        |          "version": "0.6.0"
        |        }
        |      ]
        |    },
        |    {
        |      "name": "requests[2.13.0].test",
        |      "repositories": [
        |        {
        |          "url": "https://repo1.maven.org/maven2",
        |          "type": "maven",
        |          "auth": null
        |        },
        |        {
        |          "url": "https://oss.sonatype.org/content/repositories/releases",
        |          "type": "maven",
        |          "auth": null
        |        }
        |      ],
        |      "dependencies": [
        |        {
        |          "groupId": "com.lihaoyi",
        |          "artifactId": {
        |            "name": "utest",
        |            "maybeCrossName": "utest_2.13"
        |          },
        |          "version": "0.7.3"
        |        },
        |        {
        |          "groupId": "com.lihaoyi",
        |          "artifactId": {
        |            "name": "ujson",
        |            "maybeCrossName": "ujson_2.13"
        |          },
        |          "version": "1.1.0"
        |        }
        |      ]
        |    }
        |  ]
        |}
        |""".stripMargin
    val Right(result) = parser.parseModules(data): @unchecked

    val dep12 = List("com.lihaoyi".g % ("geny", "geny_2.12").a % "0.6.0")

    assertEquals(result.headOption.map(_.dependencies), Some(dep12))

    val dep13 = List("com.lihaoyi".g % ("geny", "geny_2.13").a % "0.6.0")

    assertEquals(result.find(_.name == "requests[2.13.0]").map(_.dependencies), Some(dep13))
  }

  test("parse an IvyRepository") {
    val pattern =
      "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"
    val json = s""" { "pattern": "$pattern", "type": "ivy", "headers": [] } """
    val obtained = io.circe.parser.decode(json)(MillModule.resolverDecoder)
    val expected = Right(Resolver.IvyRepository(pattern, pattern, None, Some(Nil)))
    assertEquals(obtained, expected)
  }
}
