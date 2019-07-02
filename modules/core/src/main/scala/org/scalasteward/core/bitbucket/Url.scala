package org.scalasteward.core.bitbucket

import org.http4s.Uri
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.git.Branch

private[bitbucket] class Url(apiHost: Uri) {

  def forks(rep: Repo): Uri =
    repo(rep) / "forks"

  def listPullRequests(rep: Repo, head: String): Uri =
    pullRequests(rep).withQueryParam("q", s"""source.branch.name = "$head" """)

  def pullRequests(rep: Repo): Uri = 
    repo(rep) / "pullrequests"

  def branch(rep: Repo, branch: Branch): Uri =
    repo(rep) / "refs" / "branches" / branch.name

  def repo(repo: Repo): Uri =
    apiHost / "repositories" / repo.owner / repo.repo

}
