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

package org.scalasteward.core.mock

import org.http4s.{Request, Uri}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeAuthAlg

object MockForgeAuthAlg {
  implicit val noAuth: ForgeAuthAlg[MockEff] = new ForgeAuthAlg[MockEff] {
    override def authenticateApi(req: Request[MockEff]): MockEff[Request[MockEff]] =
      MockEff.pure(req)
    override def authenticateGit(uri: Uri): MockEff[Uri] = MockEff.pure(uri)
    override def accessibleRepos: MockEff[List[Repo]] = MockEff.pure(List.empty)
  }
}
