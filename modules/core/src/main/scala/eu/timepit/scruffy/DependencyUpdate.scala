package eu.timepit.scruffy

import cats.data.NonEmptyList
import cats.implicits._

final case class DependencyUpdate(
    groupId: String,
    artifactId: String,
    currentVersion: String,
    newerVersions: NonEmptyList[String]
)

object DependencyUpdate {
  def fromString(str: String): Either[Throwable, DependencyUpdate] =
    Either.catchNonFatal {
      val regex = """([^\s]+):([^\s]+)\s+:\s+([^\s]+)\s+->(.+)""".r
      str match {
        case regex(groupId, artifactId, version, updates) =>
          val newerVersions = NonEmptyList.fromListUnsafe(updates.split("->").map(_.trim).toList)
          DependencyUpdate(groupId, artifactId, version, newerVersions)
      }
    }
}
