/*
 * Copyright 2018-2025 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.repocache

import cats.implicits.toSemigroupKOps
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.HttpApp
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.syntax.all.*
import org.scalasteward.core.TestInstances.dummySha1
import org.scalasteward.core.data.{Repo, RepoData}
import org.scalasteward.core.forge.data.{RepoOut, UserOut}
import org.scalasteward.core.forge.github.Repository
import org.scalasteward.core.git.Branch
import org.scalasteward.core.mock.MockContext.context.{repoCacheAlg, repoConfigAlg, workspaceAlg}
import org.scalasteward.core.mock.{GitHubAuth, MockEff, MockEffOps, MockState}
import org.scalasteward.core.util.intellijThisImportIsUsed

class RepoCacheAlgTest extends CatsEffectSuite with Http4sDsl[MockEff] {
  intellijThisImportIsUsed(encodeUri)

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
    val httpApp = HttpApp[MockEff] {
      case POST -> Root / "repos" / "typelevel" / "cats-effect" / "forks" =>
        Ok(repoOut.asJson.spaces2)
      case GET -> Root / "repos" / "typelevel" / "cats-effect" / "branches" / "main" =>
        Ok(s""" { "name": "main", "commit": { "sha": "${dummySha1.value}" } } """)
      case _ => NotFound()
    }
    val authApp = GitHubAuth.api(List(Repository("typelevel/cats-effect")))
    val state = MockState.empty
      .copy(clientResponses = authApp <+> httpApp)
      .addFiles(
        workspace / "store/repo_cache/v1/github/typelevel/cats-effect/repo_cache.json" -> repoCache.asJson.spaces2
      )
    val obtained = state.flatMap(repoCacheAlg.checkCache(repo).runA)
    val expected = (RepoData(repo, repoCache, repoConfigAlg.mergeWithGlobal(None)), repoOut)
    assertIO(obtained, expected)
  }
}
