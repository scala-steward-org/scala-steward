package org.scalasteward.core.repoconfig

import cats.MonadThrow
import cats.syntax.all._
import better.files.File
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.repoconfig.RepoConfigAlg
import ValidateRepoConfigAlg._

final class ValidateRepoConfigAlg[F[_]](implicit
    fileAlg: FileAlg[F],
    F: MonadThrow[F]
) {

  def validateConfigFile(configFile: File): F[ConfigValidationResult] =
    fileAlg.readFile(configFile).map {
      case Some(content) => validateContent(content)
      case None          => ConfigValidationResult.FileDoesNotExist
    }
}

object ValidateRepoConfigAlg {
  sealed trait ConfigValidationResult

  object ConfigValidationResult {
    case object FileDoesNotExist extends ConfigValidationResult
    case class ConfigIsInvalid(err: io.circe.Error) extends ConfigValidationResult
    case object Ok extends ConfigValidationResult
  }

  def validateContent(content: String): ConfigValidationResult =
    RepoConfigAlg.parseRepoConfig(content) match {
      case Left(err) => ConfigValidationResult.ConfigIsInvalid(err)
      case Right(_)  => ConfigValidationResult.Ok
    }
}
