package org.scalasteward.core

import org.scalasteward.core.data.{ArtifactId, CrossDependency, Dependency, GroupId}
import org.scalasteward.core.util.Nel

object TestSyntax {
  implicit class StringOps(val self: String) extends AnyVal {
    def %(artifactId: String): (GroupId, ArtifactId) =
      (GroupId(self), ArtifactId(artifactId))

    def %(artifactId: ArtifactId): (GroupId, ArtifactId) =
      (GroupId(self), artifactId)

    def %(artifactIds: Nel[String]): (GroupId, Nel[ArtifactId]) =
      (GroupId(self), artifactIds.map(ArtifactId(_)))
  }

  implicit class GroupIdAndArtifactIdOps(val self: (GroupId, ArtifactId)) extends AnyVal {
    def %(version: String): Dependency =
      Dependency(self._1, self._2, version)
  }

  implicit class GroupIdAndArtifactIdsOps(val self: (GroupId, Nel[ArtifactId])) extends AnyVal {
    def %(version: String): Nel[Dependency] =
      self._2.map(artifactId => Dependency(self._1, artifactId, version))
  }

  implicit class DependencyOps(val self: Dependency) extends AnyVal {
    def %(configurations: String): Dependency =
      self.copy(configurations = Some(configurations))
  }

  implicit def dependencyToCrossDependency(dependency: Dependency): CrossDependency =
    CrossDependency(dependency)

  implicit def dependenciesToCrossDependency(dependencies: Nel[Dependency]): CrossDependency =
    CrossDependency(dependencies)

  implicit def dependenciesToCrossDependencies(
      dependencies: Nel[Dependency]
  ): Nel[CrossDependency] =
    dependencies.map(CrossDependency(_))
}
