package org.scalasteward.core.buildtool.mill

import munit.FunSuite
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Version}

class MillPluginParserTest extends FunSuite {

  val supportedVersionExamples = Map("0.9" -> "0.9.12", "0.10" -> "0.10.7")

  supportedVersionExamples.foreach { case (versionSuffix, millVersion) =>
    test(s"basic-${versionSuffix}") {
      val fileContent =
        """|import $ivy.`com.goyeau::mill-scalafix::0.2.10`
           |import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.6.1`""".stripMargin

      val expected = List(
        Dependency(
          GroupId("com.goyeau"),
          ArtifactId("mill-scalafix", s"mill-scalafix_mill${versionSuffix}_2.13"),
          Version("0.2.10")
        ),
        Dependency(
          GroupId("de.tototec"),
          ArtifactId(
            "de.tobiasroeser.mill.integrationtest",
            s"de.tobiasroeser.mill.integrationtest_mill${versionSuffix}_2.13"
          ),
          Version("0.6.1")
        )
      )

      val parsed = parser.parseMillPluginDeps(fileContent, Version(millVersion))

      assertEquals(parsed, expected)
    }
  }

}
