package org.scalasteward.core.repoconfig

import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalasteward.core.mock.{MockContext, MockState}
import org.scalasteward.core.repoconfig.ValidateRepoConfigAlg.ConfigValidationResult

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ValidateRepoConfigAlgTest extends munit.FunSuite {

  def configFile(content: String) = FunFixture[(File, ConfigValidationResult)](
    setup = _ => {
      val tmpFile =
        File(
          Files.write(
            Files.createTempFile(".scala-steward", ".conf"),
            content.getBytes(StandardCharsets.UTF_8)
          )
        )

      val obtained = MockContext.context.validateRepoConfigAlg
        .validateConfigFile(tmpFile)
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
    .test("accepts valid config") { case (file, obtained) =>
      assertEquals(
        ValidateRepoConfigAlg.presentValidationResult(file)(obtained),
        Right(s"Configuration file at $file is valid.")
      )
    }

  configFile(
    """|updates.pin  =? [
       |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
       |  { groupId = "org.scala-lang", artifactId="scala3-library_sjs1", version = "3.1." },
       |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." },
       |]""".stripMargin
  )
    .test("rejects invalid config") { case (file, obtained) =>
      val ConfigValidationResult.ConfigIsInvalid(err) = obtained
      assertEquals(
        ValidateRepoConfigAlg.presentValidationResult(file)(obtained),
        Left(s"Configuration file at $file contains errors:\n  $err")
      )

    }

  test("rejects non-existent config file") {
    // I am pretty sure this fails in Windows
    val obtained = MockContext.context.validateRepoConfigAlg
      .validateConfigFile(File("/", "scripts", "script"))
      .runA(MockState.empty)
      .unsafeRunSync()

    assertEquals(
      ValidateRepoConfigAlg.presentValidationResult(File("/", "scripts", "script"))(obtained),
      Left(s"Configuration file at /scripts/script does not exist!")
    )
  }
}
