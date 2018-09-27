package eu.timepit.scalasteward.git

import better.files.File

trait GitService[F[_]] {
  def clone(url: String, dir: File): F[Unit]
}
