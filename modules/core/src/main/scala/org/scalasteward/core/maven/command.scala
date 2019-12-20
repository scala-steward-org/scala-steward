package org.scalasteward.core.maven

object command {

  val mvnDepList = "dependency:list"
  val clean = "mvn clean"
  val mvnDepUpdates = "versions:display-dependency-updates"
  def mvnDepSet(groupId: String, artifactId: String, oldVersion: Long, newVersion: Long): String = {
    s"mvn versions:set -DgroupId=$groupId -DartifactId=$artifactId -DoldVersion=$oldVersion -DnewVersion=$newVersion"
  }
  val mvnUpdateProperties = "mvn versions:update-properties -DallowMajorUpdates=false -DallowMinorUpdates=true"
}
