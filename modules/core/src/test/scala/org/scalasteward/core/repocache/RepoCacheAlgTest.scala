package org.scalasteward.core.repocache

import io.circe.Encoder
import io.circe.generic.semiauto
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.syntax.all._
import org.scalasteward.core.TestInstances.dummySha1
import org.scalasteward.core.data.{Repo, RepoData}
import org.scalasteward.core.forge.data.{RepoOut, UserOut}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.context._
import org.scalasteward.core.mock.{MockEff, MockState}
import org.scalasteward.core.util.intellijThisImportIsUsed

class RepoCacheAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  intellijThisImportIsUsed(encodeUri)

  implicit val userOutEncoder: Encoder[UserOut] =
    semiauto.deriveEncoder

  implicit val repoOutEncoder: Encoder[RepoOut] =
    semiauto.deriveEncoder

  test("checkCache: up-to-date cache") {
    val repo = Repo("typelevel", "cats-effect")
    val repoOut = RepoOut(
      "cats-effect",
      UserOut("scala-steward"),
      Some(
        RepoOut(
          "cats-effect",
          UserOut("typelevel"),
          None,
          uri"https://github.com/typelevel/cats-effect.git",
          Branch("main")
        )
      ),
      uri"https://github.com/scala-steward/cats-effect.git",
      Branch("main")
    )
    val repoCache = RepoCache(dummySha1, Nil, None, None)
    val workspace = workspaceAlg.rootDir.unsafeRunSync()
    val state = MockState.empty
      .copy(clientResponses = HttpApp[MockEff] {
        case POST -> Root / "repos" / "typelevel" / "cats-effect" / "forks" =>
          Ok(repoOut.asJson.spaces2)
        case GET -> Root / "repos" / "typelevel" / "cats-effect" / "branches" / "main" =>
          Ok(s""" { "name": "main", "commit": { "sha": "${dummySha1.value}" } } """)
        case _ => NotFound()
      })
      .addFiles(
        workspace / "store/repo_cache/v1/github/typelevel/cats-effect/repo_cache.json" -> repoCache.asJson.spaces2
      )
    val obtained = state.flatMap(repoCacheAlg.checkCache(repo).runA)
    val expected = (RepoData(repo, repoCache, repoConfigAlg.mergeWithGlobal(None)), repoOut)
    assertIO(obtained, expected)
  }
}
