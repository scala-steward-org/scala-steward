package eu.timepit.scruffy

import cats.effect.IO

object git {
  def branchName(update: DependencyUpdate): String =
    "update/" + update.artifactId + "-" + update.nextVersion

  def checkoutBranch(repo: Repository, branch: String): IO[List[String]] =
    exec(repo, List("checkout", branch))

  def commitMsg(update: DependencyUpdate): String =
    s"Update ${update.groupId}:${update.artifactId} to ${update.nextVersion}"

  def createBranch(repo: Repository, branch: String): IO[List[String]] =
    exec(repo, List("checkout", "-b", branch))

  def currentBranch(repo: Repository): IO[String] =
    exec(repo, List("rev-parse", "--abbrev-ref", "HEAD")).map(_.mkString.trim)

  def exec(repo: Repository, cmd: List[String]): IO[List[String]] =
    io.exec("git" :: cmd, repo.root)
}
