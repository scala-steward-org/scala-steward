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
          val scalaVersion0 = moduleId.extraAttributes
            .getOrElse("e:scalaVersion", scalaVersion.value)
          val sbtVersion0 = moduleId.extraAttributes
            .get("e:sbtVersion")
            .fold("")(sbtVersion => s""", "sbtVersion" : "$sbtVersion"""" + "\n")

          s"""|{ "groupId"      : "${moduleId.organization}"
              |, "artifactId"   : "${moduleId.name}"
              |, "version"      : "${moduleId.revision}"
              |, "scalaVersion" : "${scalaVersion0}"
              |${sbtVersion0}}
              |""".stripMargin.trim
      }
      deps.mkString("[\n", ",\n", "\n]")
    }
  )
}
