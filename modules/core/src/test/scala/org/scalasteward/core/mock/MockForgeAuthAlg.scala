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
