package org.scalasteward.core.build.system

import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo

trait BuildSystemAlg[F[_]] {

  def getDependencies(repo: Repo): F[List[Dependency]]

  def getUpdatesForRepo(repo: Repo): F[List[Update.Single]]

  def runMigrations(repo: Repo, migrations: Nel[Migration]): F[Unit]

}