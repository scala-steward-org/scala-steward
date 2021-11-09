package org.scalasteward.core.repoconfig

import cats.MonadThrow
import cats.syntax.all._
import io.circe.config.parser.decode
import org.http4s.Uri
import org.scalasteward.core.application.Config.RepoConfigCfg
import org.scalasteward.core.io.FileAlg
import org.scalasteward.core.repoconfig.RepoConfigLoader.defaultRepoConfigUrl
import org.typelevel.log4cats.Logger

final class RepoConfigLoader[F[_]](implicit
    fileAlg: FileAlg[F],
    logger: Logger[F],
    F: MonadThrow[F]
) {
  def loadGlobalRepoConfig(config: RepoConfigCfg): F[Option[RepoConfig]] = {
    val maybeDefaultRepoConfigUrl =
      Option.unless(config.disableDefault)(defaultRepoConfigUrl)
    (maybeDefaultRepoConfigUrl.toList ++ config.repoConfigs)
      .traverse(loadRepoConfig)
      .flatTap(repoConfigs => logger.info(s"Loaded ${repoConfigs.size} repo config(s)"))
      .map(_.combineAllOption)
      .flatTap {
        case Some(repoConfig) => logger.info(s"Effective global repo config: $repoConfig")
        case None             => F.unit
      }
  }

  private def loadRepoConfig(uri: Uri): F[RepoConfig] =
    logger.debug(s"Loading repo config from $uri") >>
      fileAlg.readUri(uri).flatMap(decodeRepoConfig(_, uri))

  private def decodeRepoConfig(content: String, uri: Uri): F[RepoConfig] =
    F.fromEither(decode[RepoConfig](content))
      .adaptErr(new Throwable(s"Failed to load repo config from ${uri.renderString}", _))
}

object RepoConfigLoader {
  val defaultRepoConfigUrl: Uri = Uri.unsafeFromString(
    // s"https://raw.githubusercontent.com/scala-steward-org/scala-steward/${org.scalasteward.core.BuildInfo.mainBranch}/modules/core/src/main/resources/default.scala-steward.conf"
    "/home/frank/data/code/scala-steward/core/modules/core/src/main/resources/default.scala-steward.conf"
  )
}
