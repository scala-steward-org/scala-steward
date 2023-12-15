package org.scalasteward.core.repoconfig

import better.files.File
import cats.effect.unsafe.implicits.global
import cats.effect.{ExitCode, IO}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.scalasteward.core.application.ValidateRepoConfigContext

class ValidateRepoConfigAlgTest extends munit.FunSuite {

  private def configFile(content: String) = FunFixture[(File, ExitCode)](
    setup = { _ =>
      val tmpFile =
        File(
          Files.write(
            Files.createTempFile(".scala-steward", ".conf"),
            content.getBytes(StandardCharsets.UTF_8)
          )
        )

      val obtained = ValidateRepoConfigContext.run[IO](tmpFile).unsafeRunSync()
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
    val obtained = ValidateRepoConfigContext.run[IO](nonExistentFile).unsafeRunSync()

    assertEquals(obtained, ExitCode.Error)
  }
}
