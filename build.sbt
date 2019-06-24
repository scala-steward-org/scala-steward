import com.typesafe.sbt.packager.docker._
import sbtcrossproject.{CrossProject, CrossType, Platform}

/// variables

val groupId = "org.scala-steward"
val projectName = "scala-steward"
val rootPkg = groupId.replace("-", "")
val gitHubOwner = "fthomas"

val moduleCrossPlatformMatrix: Map[String, List[Platform]] = Map(
  "core" -> List(JVMPlatform)
)

/// projects

lazy val root = project
  .in(file("."))
  .aggregate(core.jvm)
  .settings(commonSettings)
  .settings(noPublishSettings)

lazy val core = myCrossProject("core")
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, DockerPlugin)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin(Dependencies.betterMonadicFor),
      compilerPlugin(Dependencies.kindProjector),
      Dependencies.betterFiles,
      Dependencies.caseApp,
      Dependencies.catsEffect,
      Dependencies.circeConfig,
      Dependencies.circeGeneric,
      Dependencies.circeParser,
      Dependencies.circeRefined,
      Dependencies.circeExtras,
      Dependencies.commonsIo,
      Dependencies.fs2Core,
      Dependencies.http4sBlazeClient,
      Dependencies.http4sCirce,
      Dependencies.log4catsSlf4j,
      Dependencies.monocleCore,
      Dependencies.refined,
      Dependencies.refinedCats,
      Dependencies.logbackClassic % Runtime,
      Dependencies.catsKernelLaws % Test,
      Dependencies.circeLiteral % Test,
      Dependencies.http4sDsl % Test,
      Dependencies.refinedScalacheck % Test,
      Dependencies.scalacheck % Test,
      Dependencies.scalaTest % Test
    ),
    assembly / test := {},
    buildInfoKeys := Seq[BuildInfoKey](scalaVersion, sbtVersion),
    buildInfoPackage := moduleRootPkg.value,
    initialCommands += s"""
      import ${moduleRootPkg.value}._
      import ${moduleRootPkg.value}.vcs.data._
      import better.files.File
      import cats.effect.ContextShift
      import cats.effect.IO
      import org.http4s.client.blaze.BlazeClientBuilder
      import scala.concurrent.ExecutionContext

      implicit val ctxShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    """,
    fork in run := true,
    fork in Test := true
  )

/// settings

def myCrossProject(name: String): CrossProject =
  CrossProject(name, file(name))(moduleCrossPlatformMatrix(name): _*)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file(s"modules/$name"))
    .settings(
      moduleName := s"$projectName-$name",
      moduleRootPkg := s"$rootPkg.$name"
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

lazy val commonSettings = Def.settings(
  compileSettings,
  metadataSettings,
  scaladocSettings
)

lazy val compileSettings = Def.settings(
  doctestTestFramework := DoctestTestFramework.ScalaTest,
  wartremoverErrors ++= Seq(Wart.TraversableOps),
  wartremoverErrors in (Compile, compile) ++= Seq(Wart.Equals)
)

lazy val metadataSettings = Def.settings(
  name := projectName,
  organization := groupId,
  homepage := Some(url(s"https://github.com/$gitHubOwner/$projectName")),
  startYear := Some(2018),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  headerLicense := Some(HeaderLicense.ALv2("2018-2019", "Scala Steward contributors")),
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
  dockerCommands := Seq(
    Cmd("FROM", "openjdk:8"),
    Cmd("ARG", "DEBIAN_FRONTEND=noninteractive"),
    ExecCmd("RUN", "apt-get", "update"),
    ExecCmd("RUN", "apt-get", "install", "-y", "apt-transport-https", "firejail"),
    ExecCmd(
      "RUN",
      "sh",
      "-c",
      """echo \"deb https://dl.bintray.com/sbt/debian /\" | tee -a /etc/apt/sources.list.d/sbt.list"""
    ),
    ExecCmd(
      "RUN",
      "apt-key",
      "adv",
      "--keyserver",
      "hkp://keyserver.ubuntu.com:80",
      "--recv",
      "2EE0EA64E40A89B84B2DF73499E82A75642AC823"
    ),
    ExecCmd("RUN", "apt-get", "update"),
    ExecCmd("RUN", "apt-get", "install", "-y", "sbt"),
    Cmd("WORKDIR", "/opt/docker"),
    Cmd("ADD", "opt", "/opt"),
    ExecCmd("ENTRYPOINT", "/opt/docker/bin/scala-steward"),
    ExecCmd("CMD", "")
  )
)

lazy val noPublishSettings = Def.settings(
  skip in publish := true
)

lazy val scaladocSettings = Def.settings()

/// setting keys

lazy val moduleRootPkg = settingKey[String]("")
moduleRootPkg := rootPkg

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

addCommandAlias(
  "runSteward", {
    val home = System.getenv("HOME")
    val projectDir = s"$home/code/scala-steward/core"
    Seq(
      Seq("core/run"),
      Seq("--workspace", s"$projectDir/workspace"),
      Seq("--repos-file", s"$projectDir/repos.md"),
      Seq("--git-author-name", "Scala Steward"),
      Seq("--git-author-email", s"me@$projectName.org"),
      Seq("--github-login", projectName),
      Seq("--git-ask-pass", s"$home/.github/askpass/$projectName.sh"),
      Seq("--whitelist", s"$home/.cache/coursier"),
      Seq("--whitelist", s"$home/.coursier"),
      Seq("--whitelist", s"$home/.ivy2"),
      Seq("--whitelist", s"$home/.sbt")
    ).flatten.mkString(" ")
  }
)
