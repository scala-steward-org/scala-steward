package eu.timepit.scruffy

import cats.data.NonEmptyList
import cats.implicits._

final case class DependencyUpdate(
    groupId: String,
    artifactId: String,
    currentVersion: String,
    newerVersions: NonEmptyList[String]
) {
  def nextVersion: String =
    newerVersions.head
}

object DependencyUpdate {
  def fromString(str: String): Either[Throwable, DependencyUpdate] =
    Either.catchNonFatal {
      val delim = "->"
      val regex = s"""([^\s]+):([^\s]+)\s+:\s+([^\s]+)\s+$delim(.+)""".r
      str match {
        case regex(groupId, artifactId, version, updates) =>
          val newerVersions = NonEmptyList.fromListUnsafe(updates.split(delim).map(_.trim).toList)
          DependencyUpdate(groupId, artifactId, version, newerVersions)
      }
    }
}
