package org.scalasteward.core.repoconfig

import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalasteward.core.mock.MockContext
import org.scalasteward.core.mock.MockState
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import org.scalasteward.core.repoconfig.ValidateRepoConfigAlg.ConfigValidationResult

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
    """|updates.pin  =? [
       |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
       |  { groupId = "org.scala-lang", artifactId="scala3-library_sjs1", version = "3.1." },
       |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." },
       |]""".stripMargin
  )
    .test("rejects invalid config") { case (_, obtained) =>
      obtained match {
        case ConfigValidationResult.ConfigIsInvalid(_) => assert(true)
        case other => fail(s"Invalid config was accepted with: $other")
      }

    }

  configFile(
    """|updates.pin = [
       |  { groupId = "org.scala-lang", artifactId="scala3-library", version = "3.1." },
       |  { groupId = "org.scala-lang", artifactId="scala3-library_sjs1", version = "3.1." },
       |  { groupId = "org.scala-js", artifactId="sbt-scalajs", version = "1.10." }
       |]""".stripMargin
  )
    .test("accepts valid config") { case (_, obtained) =>
      obtained match {
        case ConfigValidationResult.Ok => assert(true)
        case other =>
          fail(s"Valid config was rejected with: $other")
      }
    }

  test("rejects non-existent config file") {
    val obtained = MockContext.context.validateRepoConfigAlg
      .validateConfigFile(File("scripts", "script"))
      .runA(MockState.empty)
      .unsafeRunSync()

    obtained match {
      case ConfigValidationResult.FileDoesNotExist => assert(true)
      case other => fail(s"Non existent file was accepted with: $other")
    }

  }
}
