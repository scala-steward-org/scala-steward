package eu.timepit.scalasteward
import better.files.File

final case class LocalRepo(
    upstream: GithubRepo,
    dir: File
)
