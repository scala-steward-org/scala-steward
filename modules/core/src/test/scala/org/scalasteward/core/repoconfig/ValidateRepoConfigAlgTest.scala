package org.scalasteward.core.repoconfig

import better.files.File
import cats.effect.ExitCode
import cats.effect.unsafe.implicits.global
import org.scalasteward.core.mock.{MockContext, MockState}

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ValidateRepoConfigAlgTest extends munit.FunSuite {

  def configFile(content: String) = FunFixture[(File, ExitCode)](
    setup = { _ =>
      val tmpFile =
        File(
          Files.write(
            Files.createTempFile(".scala-steward", ".conf"),
            content.getBytes(StandardCharsets.UTF_8)
          )
        )

      val obtained = MockContext
        .validateRepoConfigContext(tmpFile)
        .runF
        .runA(MockState.empty)
        .unsafeRunSync()

      (tmpFile, obtained)
    },
    teardown = { case (file, _) =>
      file.delete()
    }
  )

  configFile(
    """|updates.pin = [
       |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
       |  { groupId = "org.scala-lang", artifactId="scala3-library_sjs1", version = "3.1." },
       |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." }
       |]""".stripMargin
  )
    .test("accepts valid config") { case (_, obtained) =>
      assertEquals(obtained, ExitCode.Success)
    }

  configFile(
    """|updates.pin  =? [
       |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
       |  { groupId = "org.scala-lang", artifactId="scala3-library_sjs1", version = "3.1." },
       |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." },
       |]""".stripMargin
  )
    .test("rejects config with a parsing failure") { case (_, obtained) =>
      assertEquals(obtained, ExitCode.Error)
    }

  configFile(
    """|updatePullRequests = 123
       |
       |""".stripMargin
  )
    .test("rejects config with a decoding failure") { case (_, obtained) =>
      assertEquals(obtained, ExitCode.Error)
    }

  test("rejects non-existent config file") {
    val nonExistentFile = File("/", "scripts", "script")
    val obtained = MockContext
      .validateRepoConfigContext(nonExistentFile)
      .runF
      .runA(MockState.empty)
      .unsafeRunSync()

    assertEquals(obtained, ExitCode.Error)
  }
}
