package org.scalasteward.core.repoconfig

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import org.http4s.Uri
import org.scalasteward.core.application.Config.RepoConfigCfg
import org.scalasteward.core.mock.MockConfig.mockRoot
import org.scalasteward.core.mock.MockContext.context.{fileAlg, logger}
import org.scalasteward.core.mock.MockContext.mockState

class RepoConfigLoaderTest extends FunSuite {
  test("config file merging order") {
    val config1 =
      """
        |updates.pin = [
        |  { groupId = "org.scala-lang", artifactId="scala3-compiler", version = "3.3." },
        |  { groupId = "org.scala-lang", artifactId="scala3-compiler", version = "3.5." },
        |]
        |""".stripMargin
    val config2 =
      """
        |updates.pin = [
        |  { groupId = "org.scala-lang", artifactId="scala3-compiler", version = "3.4." },
        |]
        |""".stripMargin

    val uri1 = Uri.unsafeFromString(s"$mockRoot/test1.scala-steward.conf")
    val uri2 = Uri.unsafeFromString(s"$mockRoot/test2.scala-steward.conf")

    val initialState = mockState.addUris(uri1 -> config1, uri2 -> config2)

    val repoConfigLoader = new RepoConfigLoader
    val repoConfig = repoConfigLoader
      .loadGlobalRepoConfig(RepoConfigCfg(repoConfigs = List(uri1, uri2), disableDefault = true))
      .runA(initialState)
      .attempt
      .unsafeRunSync()
      .getOrElse(None)
    assert(clue(repoConfig).isDefined)
    assertEquals(repoConfig.get.updates.pin.head.version, Some(VersionPattern(Some("3.4."))))
  }
}
