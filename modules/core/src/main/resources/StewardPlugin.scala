import sbt.Keys._
import sbt._

object StewardPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val libraryDependenciesAsJson = settingKey[String]("")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependenciesAsJson := {
      val deps = libraryDependencies.value.map {
        moduleId =>
          val entries: List[(String, String)] = List(
            "groupId" -> moduleId.organization,
            "artifactId" -> moduleId.name,
            "version" -> moduleId.revision,
            "scalaVersion" -> moduleId.extraAttributes
              .getOrElse("e:scalaVersion", scalaVersion.value)
          ) ++
            moduleId.extraAttributes
              .get("e:sbtVersion")
              .map(sbtVersion => "sbtVersion" -> sbtVersion)
              .toList

          entries.map { case (k, v) => s""""$k": "$v"""" }.mkString("{ ", ", ", " }")
      }
      deps.mkString("[ ", ", ", " ]")
    }
  )
}
