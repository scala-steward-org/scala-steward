package eu.timepit.scruffy

import java.nio.file.{Path, Paths}

import cats.data.NonEmptyList

//import cats.implicits._

//import scala.sys.process._

object Main {
  def main(args: Array[String]): Unit = {
    val repos = List("fthomas/refined")
    val workspace = Paths.get("/home/frank/code/scruffy/workspace")
    workspace.toFile.mkdirs()
    cloneRepos(repos, workspace)
    ()
  }

  def cloneRepos(repos: List[String], workspace: Path): Unit =
    repos.foreach { repo =>
      val repoDir = workspace.resolve(repo)
      cloneRepo(repo, repoDir)
    }

  def cloneRepo(repo: String, repoDir: Path): Unit = {
    val url = "https://github.com/" + repo
    val cmd = s"git clone $url $repoDir"
    println(cmd)
    println(url)
    println(repoDir)
    /*val p = io.deleteRecursively(repoDir) >> io.mkdirs(repoDir)
    p.unsafeRunSync()
    cmd.!!
     */
    // can we set up sbt plugins in workspace ?
    //sbt.dependencyUpdates(repoDir).map(println).unsafeRunSync()
    val update =
      DependencyUpdate("com.github.pureconfig", "pureconfig", "0.9.1", NonEmptyList.of("0.9.2"))
    val repo = Repository(repoDir)
    git.checkoutBranch(repo, update)
  }

  //def gitClone(url: String, repoDir: Path) =
  //  io.execLines(List("git", "clone", url, repoDir.toString), repoDir)

}
