package org.scalasteward.core.buildtool.maven

import munit.CatsEffectSuite
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.BuildRoot
import org.scalasteward.core.data.Resolver.MavenRepository
import org.scalasteward.core.data.{Repo, Scope}
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.{MockEffOps, MockState}

class MavenAlgTest extends CatsEffectSuite {
  test("getDependencies") {
    val repo = Repo("maven-alg", "test-1")
    val buildRoot = BuildRoot(repo, ".")
    val repoDir = workspaceAlg.repoDir(repo).unsafeRunSync()

    val obtained = MockState.empty
      .copy(execCommands = true)
      .addFiles(
        repoDir / "pom.xml" ->
          """|<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             |         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
             |  <modelVersion>4.0.0</modelVersion>
             |  <groupId>com.mycompany.app</groupId>
             |  <artifactId>my-app</artifactId>
             |  <version>1.0-SNAPSHOT</version>
             |  <dependencies>
             |      <dependency>
             |          <groupId>org.typelevel</groupId>
             |          <artifactId>cats-effect_2.13</artifactId>
             |          <version>3.5.2</version>
             |      </dependency>
             |  </dependencies>
             |  <repositories>
             |    <repository>
             |      <id>central</id>
             |      <url>https://repo1.maven.org/maven2/</url>
             |    </repository>
             |  </repositories>
             |</project>
             |""".stripMargin
      )
      .flatMap(mavenAlg.getDependencies(buildRoot).runA)

    val expected = List(
      Scope(
        List("org.typelevel".g % ("cats-effect", "cats-effect_2.13").a % "3.5.2"),
        List(MavenRepository("central", "https://repo1.maven.org/maven2/", None, None))
      )
    )

    assertIO(obtained, expected)
  }
}
