package org.scalasteward.core.buildtool.mill

import munit.FunSuite
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId}

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
    val Right(result) = parser.parseModules(data)

    val dep12 = List(
      Dependency(GroupId("com.lihaoyi"), ArtifactId("geny", Some("geny_2.12")), "0.6.0")
    )

    assertEquals(result.headOption.map(_.dependencies), Some(dep12))

    val dep13 = List(
      Dependency(GroupId("com.lihaoyi"), ArtifactId("geny", Some("geny_2.13")), "0.6.0")
    )

    assertEquals(result.find(_.name == "requests[2.13.0]").map(_.dependencies), Some(dep13))
  }
}
