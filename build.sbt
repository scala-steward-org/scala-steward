import com.typesafe.sbt.packager.docker._
import sbtcrossproject.{CrossProject, CrossType, Platform}

/// variables

val groupId = "org.scala-steward"
val projectName = "scala-steward"
val rootPkg = groupId.replace("-", "")
val gitHubOwner = "scala-steward-org"

val moduleCrossPlatformMatrix: Map[String, List[Platform]] = Map(
  "core" -> List(JVMPlatform),
  "sbt-plugin" -> List(JVMPlatform),
  "mill-plugin" -> List(JVMPlatform)
)

val Scala212 = "2.12.10"
val Scala213 = "2.13.3"

///

ThisBuild / crossScalaVersions := Seq(Scala212, Scala213)
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches += RefPredicate.StartsWith(Ref.Tag("v"))
ThisBuild / githubWorkflowPublish := Seq(WorkflowStep.Sbt(List("ci-release")))
ThisBuild / githubWorkflowJavaVersions := Seq("adopt@1.8", "adopt@1.11")
ThisBuild / githubWorkflowBuild :=
  Seq(WorkflowStep.Sbt(List("validate"), name = Some("Build project")))

/// projects

lazy val root = project
  .in(file("."))
  .aggregate(core.jvm, `sbt-plugin`.jvm, `mill-plugin`.jvm)
  .settings(commonSettings)
  .settings(noPublishSettings)

lazy val core = myCrossProject("core")
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, DockerPlugin)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin(Dependencies.betterMonadicFor),
      compilerPlugin(Dependencies.kindProjector.cross(CrossVersion.full)),
      Dependencies.attoCore,
      Dependencies.betterFiles,
      Dependencies.caseApp,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.circeConfig,
      Dependencies.circeGeneric,
      Dependencies.circeGenericExtras,
      Dependencies.circeParser,
      Dependencies.circeRefined,
      Dependencies.commonsIo,
      Dependencies.coursierCore,
      Dependencies.coursierCatsInterop,
      Dependencies.cron4sCore,
      Dependencies.fs2Core,
      Dependencies.fs2Io,
      Dependencies.http4sCirce,
      Dependencies.http4sClient,
      Dependencies.http4sCore,
      Dependencies.http4sOkhttpClient,
      Dependencies.log4catsSlf4j,
      Dependencies.monocleCore,
      Dependencies.refined,
      Dependencies.refinedCats,
      Dependencies.scalacacheCaffeine,
      Dependencies.scalacacheCatsEffect,
      Dependencies.logbackClassic % Runtime,
      Dependencies.catsLaws % Test,
      Dependencies.circeLiteral % Test,
      Dependencies.disciplineScalatest % Test,
      Dependencies.http4sDsl % Test,
      Dependencies.refinedScalacheck % Test,
      Dependencies.scalacheck % Test,
      Dependencies.scalaTestFunSuite % Test,
      Dependencies.scalaTestShouldMatcher % Test
    ),
    assembly / test := {},
    assemblyMergeStrategy in assembly := {
      val nativeSuffix = "\\.(?:dll|jnilib|so)$".r

      {
        case PathList(ps @ _*) if nativeSuffix.findFirstMatchIn(ps.last).isDefined =>
          MergeStrategy.first
        case PathList(ps @ _*) if ps.last == "io.netty.versions.properties" =>
          // This is included in Netty JARs which are pulled in by http4s-async-http-client.
          MergeStrategy.first
        case PathList("org", "fusesource", _*) =>
          // (core / assembly) deduplicate: different file contents found in the following:
          // https/repo1.maven.org/maven2/jline/jline/2.14.6/jline-2.14.6.jar:org/fusesource/hawtjni/runtime/Callback.class
          // https/repo1.maven.org/maven2/org/fusesource/jansi/jansi/1.18/jansi-1.18.jar:org/fusesource/hawtjni/runtime/Callback.class
          MergeStrategy.first
        case otherwise =>
          val defaultStrategy = (assemblyMergeStrategy in assembly).value
          defaultStrategy(otherwise)
      }
    },
    buildInfoKeys := Seq[BuildInfoKey](
      organization,
      version,
      scalaVersion,
      scalaBinaryVersion,
      sbtVersion,
      BuildInfoKey.map(git.gitHeadCommit) { case (k, v) => k -> v.getOrElse("master") },
      BuildInfoKey.map(`sbt-plugin`.jvm / moduleRootPkg) { case (_, v) =>
        "sbtPluginModuleRootPkg" -> v
      },
      BuildInfoKey.map(`mill-plugin`.jvm / moduleName) { case (_, v) =>
        "millPluginModuleName" -> v
      },
      BuildInfoKey.map(`mill-plugin`.jvm / moduleRootPkg) { case (_, v) =>
        "millPluginModuleRootPkg" -> v
      }
    ),
    buildInfoPackage := moduleRootPkg.value,
    initialCommands += s"""
      import ${moduleRootPkg.value}._
      import ${moduleRootPkg.value}.data._
      import ${moduleRootPkg.value}.util.Nel
      import ${moduleRootPkg.value}.vcs.data._
      import better.files.File
      import cats.effect.ContextShift
      import cats.effect.IO
      import cats.effect.Timer
      import _root_.io.chrisdavenport.log4cats.Logger
      import _root_.io.chrisdavenport.log4cats.slf4j.Slf4jLogger
      import org.http4s.client.Client
      import org.http4s.client.asynchttpclient.AsyncHttpClient
      import scala.concurrent.ExecutionContext

      implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
      implicit val ioTimer: Timer[IO] = IO.timer(ExecutionContext.global)
      implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
      implicit val client: Client[IO] = AsyncHttpClient.allocate[IO]().map(_._1).unsafeRunSync()
    """,
    fork in run := true,
    fork in Test := true,
    Compile / unmanagedResourceDirectories ++= (`sbt-plugin`.jvm / Compile / unmanagedSourceDirectories).value
  )

lazy val `sbt-plugin` = myCrossProject("sbt-plugin")
  .settings(noPublishSettings)
  .settings(
    scalaVersion := Scala212,
    sbtPlugin := true,
    Compile / compile / wartremoverErrors -= Wart.Equals
  )

lazy val `mill-plugin` = myCrossProject("mill-plugin")
  .settings(
    crossScalaVersions := Seq(Scala213, Scala212),
    libraryDependencies += Dependencies.millScalalib.value % Provided
  )

/// settings

def myCrossProject(name: String): CrossProject =
  CrossProject(name, file(name))(moduleCrossPlatformMatrix(name): _*)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file(s"modules/$name"))
    .settings(
      moduleName := s"$projectName-$name",
      moduleRootPkg := s"$rootPkg.${name.replace('-', '.')}"
    )
    .settings(commonSettings)
    // workaround for https://github.com/portable-scala/sbt-crossproject/issues/74
    .settings(Seq(Compile, Test).flatMap(inConfig(_) {
      unmanagedResourceDirectories ++= {
        unmanagedSourceDirectories.value
          .map(src => (src / ".." / "resources").getCanonicalFile)
          .filterNot(unmanagedResourceDirectories.value.contains)
          .distinct
      }
    }))

ThisBuild / dynverSeparator := "-"

lazy val commonSettings = Def.settings(
  compileSettings,
  metadataSettings,
  scaladocSettings
)

lazy val compileSettings = Def.settings(
  scalaVersion := Scala213,
  doctestTestFramework := DoctestTestFramework.ScalaCheck,
  wartremoverErrors ++= Seq(Wart.TraversableOps),
  Compile / compile / wartremoverErrors ++= Seq(Wart.Equals)
)

lazy val metadataSettings = Def.settings(
  name := projectName,
  organization := groupId,
  homepage := Some(url(s"https://github.com/$gitHubOwner/$projectName")),
  startYear := Some(2018),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(
    ScmInfo(homepage.value.get, s"scm:git:https://github.com/$gitHubOwner/$projectName.git")
  ),
  headerLicense := Some(HeaderLicense.ALv2("2018-2020", "Scala Steward contributors")),
  developers := List(
    Developer(
      id = "fthomas",
      name = "Frank S. Thomas",
      email = "",
      url(s"https://github.com/fthomas")
    )
  )
)

lazy val dockerSettings = Def.settings(
  dockerBaseImage := Option(System.getenv("DOCKER_BASE_IMAGE")).getOrElse("openjdk:8-jdk-alpine"),
  dockerCommands ++= {
    val getSbtVersion = sbtVersion.value
    val sbtTgz = s"sbt-$getSbtVersion.tgz"
    val sbtUrl = s"https://github.com/sbt/sbt/releases/download/v$getSbtVersion/$sbtTgz"
    val millVersion = Dependencies.millVersion.value
    val binDir = "/usr/local/bin/"
    Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "apk --no-cache add bash git ca-certificates curl maven openssh"),
      Cmd("RUN", s"wget $sbtUrl && tar -xf $sbtTgz && rm -f $sbtTgz"),
      Cmd(
        "RUN",
        s"curl -L https://github.com/lihaoyi/mill/releases/download/${millVersion.split("-").head}/$millVersion >${binDir}mill && chmod +x ${binDir}mill"
      ),
      Cmd(
        "RUN",
        s"curl -L https://git.io/coursier-cli > ${binDir}coursier && chmod +x ${binDir}coursier && coursier install scalafmt --install-dir ${binDir} && rm -rf ${binDir}coursier"
      )
    )
  },
  Docker / packageName := s"fthomas/${name.value}",
  dockerUpdateLatest := true,
  dockerEntrypoint += "--disable-sandbox",
  dockerEnvVars := Map("PATH" -> "/opt/docker/sbt/bin:${PATH}")
)

lazy val noPublishSettings = Def.settings(
  skip in publish := true
)

lazy val scaladocSettings = Def.settings(
  Compile / doc / scalacOptions ++= {
    val tag = s"v${version.value}"
    val tree = if (isSnapshot.value) git.gitHeadCommit.value.getOrElse("master") else tag
    Seq(
      "-doc-source-url",
      s"${scmInfo.value.get.browseUrl}/blob/${tree}€{FILE_PATH}.scala",
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath
    )
  }
)

/// setting keys

lazy val installPlugin = taskKey[Unit]("Copies StewardPlugin.scala into global plugins directory.")
installPlugin := {
  val name = "StewardPlugin.scala"
  val source = (`sbt-plugin`.jvm / Compile / sources).value.find(_.name == name).get
  val target = file(System.getProperty("user.home")) / ".sbt" / "1.0" / "plugins" / name
  IO.copyFile(source, target)
}

lazy val moduleRootPkg = settingKey[String]("")
moduleRootPkg := rootPkg

// Run Scala Steward from sbt for development and testing.
// Do not do this in production.
lazy val runSteward = taskKey[Unit]("")
runSteward := Def.taskDyn {
  val home = System.getenv("HOME")
  val projectDir = (LocalRootProject / baseDirectory).value
  val args = Seq(
    Seq("--workspace", s"$projectDir/workspace"),
    Seq("--repos-file", s"$projectDir/repos.md"),
    Seq("--default-repo-conf", s"$projectDir/default.scala-steward.conf"),
    Seq("--git-author-email", s"me@$projectName.org"),
    Seq("--vcs-login", projectName),
    Seq("--git-ask-pass", s"$home/.github/askpass/$projectName.sh"),
    Seq("--whitelist", s"$home/.cache/coursier"),
    Seq("--whitelist", s"$home/.cache/JNA"),
    Seq("--whitelist", s"$home/.cache/mill"),
    Seq("--whitelist", s"$home/.ivy2"),
    Seq("--whitelist", s"$home/.m2"),
    Seq("--whitelist", s"$home/.mill"),
    Seq("--whitelist", s"$home/.sbt")
  ).flatten.mkString(" ", " ", "")
  (core.jvm / Compile / run).toTask(args)
}.value

/// commands

def addCommandsAlias(name: String, cmds: Seq[String]) =
  addCommandAlias(name, cmds.mkString(";", ";", ""))

addCommandsAlias(
  "validate",
  Seq(
    "clean",
    "headerCheck",
    "scalafmtCheck",
    "scalafmtSbtCheck",
    "test:scalafmtCheck",
    "unusedCompileDependenciesTest",
    "coverage",
    "test",
    "coverageReport",
    "doc",
    "package",
    "packageSrc",
    "core/assembly",
    "docker:publishLocal"
  )
)

addCommandsAlias(
  "fmt",
  Seq(
    "headerCreate",
    "scalafmt",
    "test:scalafmt",
    "scalafmtSbt"
  )
)
