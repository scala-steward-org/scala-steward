package org.scalasteward.core

import org.scalasteward.core.data.Resolver.IvyRepository
import org.scalasteward.core.data._
import org.scalasteward.core.util.Nel

object TestSyntax {
  val sbtPluginReleases: IvyRepository =
    IvyRepository(
      "sbt-plugin-releases",
      "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[defaultPattern]",
      None
    )

  implicit class GenericOps[A](val self: A) extends AnyVal {
    def withMavenCentral: Scope[A] =
      Scope(self, List(Resolver.mavenCentral))
  }

  implicit class StringOps(private val self: String) extends AnyVal {
    def g: GroupId = GroupId(self)
    def a: ArtifactId = ArtifactId(self)
    def v: Version = Version(self)
  }

  implicit class StringTupleOps(private val self: (String, String)) extends AnyVal {
    def a: ArtifactId = ArtifactId(self._1, self._2)
  }

  implicit class GroupIdOps(private val self: GroupId) {
    def %(artifactId: ArtifactId): (GroupId, ArtifactId) = (self, artifactId)
    def %(artifactIds: Nel[ArtifactId]): (GroupId, Nel[ArtifactId]) = (self, artifactIds)
    def %%(artifactIds: Nel[Nel[ArtifactId]]): (GroupId, Nel[Nel[ArtifactId]]) = (self, artifactIds)
  }

  implicit class GroupIdAndArtifactIdOps(private val self: (GroupId, ArtifactId)) extends AnyVal {
    def %(version: String): Dependency = Dependency(self._1, self._2, version.v)
  }

  implicit class GroupIdAndArtifactIdsOps(
      private val self: (GroupId, Nel[ArtifactId])
  ) extends AnyVal {
    def %(version: String): (GroupId, Nel[ArtifactId], String) = (self._1, self._2, version)
  }

  implicit class GroupIdAndManyArtifactIdsOps(
      private val self: (GroupId, Nel[Nel[ArtifactId]])
  ) extends AnyVal {
    def %(version: String): (GroupId, Nel[Nel[ArtifactId]], String) = (self._1, self._2, version)
  }

  implicit class DependencyOps(private val self: Dependency) extends AnyVal {
    def %(configurations: String): Dependency = self.copy(configurations = Some(configurations))
    def %>(nextVersion: String): (Dependency, String) = (self, nextVersion)
    def %>(newerVersions: Nel[String]): (Dependency, Nel[String]) = (self, newerVersions)
    def cross: CrossDependency = CrossDependency(self)
  }

  implicit class DependenciesOps(private val self: Nel[Dependency]) extends AnyVal {
    def %>(nextVersion: String): (Nel[Dependency], String) = (self, nextVersion)
  }

  implicit class GroupIdAndArtifactIdsAndVersionOps(
      private val self: (GroupId, Nel[ArtifactId], String)
  ) extends AnyVal {
    def %>(nextVersion: String): (GroupId, Nel[ArtifactId], String, String) =
      (self._1, self._2, self._3, nextVersion)
  }

  implicit class GroupIdAndManyArtifactIdsAndVersionOps(
      private val self: (GroupId, Nel[Nel[ArtifactId]], String)
  ) extends AnyVal {
    def %>(nextVersion: String): (GroupId, Nel[Nel[ArtifactId]], String, String) =
      (self._1, self._2, self._3, nextVersion)
  }

  implicit class DependencyAndNextVersionOps(
      private val self: (Dependency, String)
  ) extends AnyVal {
    def single: Update.ForArtifactId =
      Update.ForArtifactId(CrossDependency(self._1), Nel.of(self._2.v))
  }

  implicit class DependencyAndNewerVersionsOps(
      private val self: (Dependency, Nel[String])
  ) extends AnyVal {
    def single: Update.ForArtifactId =
      Update.ForArtifactId(CrossDependency(self._1), self._2.map(_.v))
  }

  implicit class DependenciesAndNextVersionOps(
      private val self: (Nel[Dependency], String)
  ) extends AnyVal {
    def single: Update.ForArtifactId =
      Update.ForArtifactId(CrossDependency(self._1), Nel.of(self._2.v))
  }

  implicit class GroupIdAndArtifactIdsAndVersionAndNextVersionOps(
      private val self: (GroupId, Nel[ArtifactId], String, String)
  ) extends AnyVal {
    def single: Update.ForArtifactId = {
      val crossDependency = CrossDependency(self._2.map(aId => Dependency(self._1, aId, self._3.v)))
      Update.ForArtifactId(crossDependency, Nel.of(self._4.v))
    }

    def group: Update.ForGroupId = {
      val crossDependencies =
        self._2.map(aId => CrossDependency(Dependency(self._1, aId, self._3.v)))
      Update.ForGroupId(crossDependencies, Nel.of(self._4.v))
    }
  }

  implicit class GroupIdAndManyArtifactIdsAndVersionAndNextVersionOps(
      private val self: (GroupId, Nel[Nel[ArtifactId]], String, String)
  ) extends AnyVal {
    def group: Update.ForGroupId = {
      val crossDependencies =
        self._2.map(aIds => CrossDependency(aIds.map(aId => Dependency(self._1, aId, self._3.v))))
      Update.ForGroupId(crossDependencies, Nel.of(self._4.v))
    }
  }
}
