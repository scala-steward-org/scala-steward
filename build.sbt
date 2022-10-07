import com.typesafe.sbt.packager.docker._
import sbtcrossproject.{CrossProject, CrossType, Platform}
import sbtghactions.JavaSpec.Distribution.Adopt

/// variables

val groupId = "org.scala-steward"
val projectName = "scala-steward"
val rootPkg = groupId.replace("-", "")
val gitHubOwner = "scala-steward-org"
val gitHubUrl = s"https://github.com/$gitHubOwner/$projectName"
val mainBranch = "main"
val gitHubUserContent = s"https://raw.githubusercontent.com/$gitHubOwner/$projectName/$mainBranch"

val moduleCrossPlatformMatrix: Map[String, List[Platform]] = Map(
  "benchmark" -> List(JVMPlatform),
  "core" -> List(JVMPlatform),
  "docs" -> List(JVMPlatform),
  "sbt-plugin" -> List(JVMPlatform),
  "mill-plugin" -> List(JVMPlatform)
)

val Scala212 = "2.12.17"
val Scala213 = "2.13.8"

/// sbt-github-actions configuration

ThisBuild / crossScalaVersions := Seq(Scala212, Scala213)
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch(mainBranch)),
  RefPredicate.StartsWith(Ref.Tag("v"))
)
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Run(
    List("sbt ci-release"),
    name = Some("Publish JARs"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  ),
  WorkflowStep.Run(
    List(
      "docker login -u ${{ secrets.DOCKER_USERNAME }} -p ${{ secrets.DOCKER_PASSWORD }}",
      "sbt core/Docker/publish"
    ),
    name = Some("Publish Docker image")
  )
)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec(Adopt, "8"), JavaSpec(Adopt, "11"))
ThisBuild / githubWorkflowBuild :=
  Seq(
    WorkflowStep.Sbt(List("validate"), name = Some("Build project")),
    WorkflowStep.Use(
      UseRef.Public("codecov", "codecov-action", "v2"),
      name = Some("Codecov")
    )
  )

/// global build settings

ThisBuild / evictionErrorLevel := Level.Info

/// projects

lazy val root = project
  .in(file("."))
  .aggregate(benchmark.jvm, core.jvm, docs.jvm, `sbt-plugin`.jvm, `mill-plugin`.jvm)
  .settings(commonSettings)
  .settings(noPublishSettings)

lazy val benchmark = myCrossProject("benchmark")
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .settings(noPublishSettings)
  .settings(
    coverageEnabled := false,
    unusedCompileDependencies := Set.empty
  )

lazy val core = myCrossProject("core")
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, DockerPlugin)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.bcprovJdk15to18,
      Dependencies.betterFiles,
      Dependencies.catsCore,
      Dependencies.catsEffect,
      Dependencies.catsParse,
      Dependencies.circeConfig,
      Dependencies.circeGeneric,
      Dependencies.circeGenericExtras,
      Dependencies.circeParser,
      Dependencies.circeRefined,
      Dependencies.commonsIo,
      Dependencies.coursierCore,
      Dependencies.cron4sCore,
      Dependencies.decline,
      Dependencies.fs2Core,
      Dependencies.fs2Io,
      Dependencies.http4sCirce,
      Dependencies.http4sClient,
      Dependencies.http4sCore,
      Dependencies.http4sOkhttpClient,
      Dependencies.jjwtApi,
      Dependencies.jjwtImpl % Runtime,
      Dependencies.jjwtJackson % Runtime,
      Dependencies.log4catsSlf4j,
      Dependencies.monocleCore,
      Dependencies.refined,
      Dependencies.scalacacheCaffeine,
      Dependencies.logbackClassic % Runtime,
      Dependencies.catsLaws % Test,
      Dependencies.circeLiteral % Test,
      Dependencies.disciplineMunit % Test,
      Dependencies.http4sDsl % Test,
      Dependencies.http4sBlazeServer % Test,
      Dependencies.munit % Test,
      Dependencies.munitCatsEffect % Test,
      Dependencies.munitScalacheck % Test,
      Dependencies.refinedScalacheck % Test,
      Dependencies.scalacheck % Test
    ),
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "versions", "9", "module-info.class") =>
        // (core / assembly) deduplicate: different file contents found in the following:
        // https/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.4.20/kotlin-stdlib-1.4.20.jar:META-INF/versions/9/module-info.class
        // https/repo1.maven.org/maven2/org/tukaani/xz/1.9/xz-1.9.jar:META-INF/versions/9/module-info.class
        MergeStrategy.first
      case PathList("module-info.class") =>
        // (core / assembly) deduplicate: different file contents found in the following:
        // https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.12.6/jackson-annotations-2.12.6.jar:module-info.class
        // https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.12.6/jackson-core-2.12.6.jar:module-info.class
        // https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.12.6.1/jackson-databind-2.12.6.1.jar:module-info.class
        MergeStrategy.discard
      case otherwise =>
        val defaultStrategy = (assembly / assemblyMergeStrategy).value
        defaultStrategy(otherwise)
    },
    buildInfoKeys := Seq[BuildInfoKey](
      organization,
      version,
      scalaVersion,
      scalaBinaryVersion,
      sbtVersion,
      BuildInfoKey("gitHubUrl" -> gitHubUrl),
      BuildInfoKey("gitHubUserContent" -> gitHubUserContent),
      BuildInfoKey("mainBranch" -> mainBranch),
      BuildInfoKey.map(git.gitHeadCommit) { case (k, v) => k -> v.getOrElse(mainBranch) },
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
      import cats.effect.IO
      import org.http4s.client.Client
      import org.typelevel.log4cats.Logger
      import org.typelevel.log4cats.slf4j.Slf4jLogger

      implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
    """,
    // Inspired by https://stackoverflow.com/a/41978937/460387
    Test / sourceGenerators += Def.task {
      val file = (Test / sourceManaged).value / "InitialCommandsTest.scala"
      val content =
        s"""object InitialCommandsTest {
           |  ${initialCommands.value}
           |  // prevent warnings
           |  locally(Client); locally(File); locally(Nel); locally(Repo);
           |  locally(Version); locally(data.Version);
           |}""".stripMargin
      IO.write(file, content)
      Seq(file)
    }.taskValue,
    run / fork := true,
    Test / fork := true,
    Compile / unmanagedResourceDirectories ++= (`sbt-plugin`.jvm / Compile / unmanagedSourceDirectories).value
  )

lazy val docs = myCrossProject("docs")
  .dependsOn(core)
  .enablePlugins(MdocPlugin)
  .settings(noPublishSettings)
  .settings(
    mdocIn := baseDirectory.value / ".." / "mdoc",
    mdocOut := (LocalRootProject / baseDirectory).value / "docs",
    mdocVariables := Map(
      "GITHUB_URL" -> gitHubUrl,
      "MAIN_BRANCH" -> mainBranch
    ),
    checkDocs := {
      val inDir = mdocIn.value.getCanonicalPath
      val outDir = mdocOut.value.getCanonicalPath
      val rootDir = (LocalRootProject / baseDirectory).value
      try git.runner.value.apply("diff", "--quiet", outDir)(rootDir, streams.value.log)
      catch {
        case t: Throwable =>
          val msg = s"Docs in $inDir and $outDir are out of sync." +
            " Run 'sbt docs/mdoc' and commit the changes to fix this."
          throw new Throwable(msg, t)
      }
      ()
    },
    coverageEnabled := false,
    unusedCompileDependencies := Set.empty
  )

lazy val `sbt-plugin` = myCrossProject("sbt-plugin")
  .settings(noPublishSettings)
  .settings(
    scalaVersion := Scala212,
    sbtPlugin := true
  )

lazy val `mill-plugin` = myCrossProject("mill-plugin")
  .settings(
    crossScalaVersions := Seq(Scala213, Scala212),
    libraryDependencies += Dependencies.millScalalib.value % Provided,
    scalacOptions -= "-Xfatal-warnings"
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
  doctestTestFramework := DoctestTestFramework.Munit
)

lazy val metadataSettings = Def.settings(
  name := projectName,
  organization := groupId,
  homepage := Some(url(gitHubUrl)),
  startYear := Some(2018),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(ScmInfo(homepage.value.get, s"scm:git:$gitHubUrl.git")),
  headerLicense := Some(HeaderLicense.ALv2("2018-2022", "Scala Steward contributors")),
  developers := List(
    Developer(
      id = "exoego",
      name = "TATSUNO Yasuhiro",
      email = "",
      url(s"https://github.com/exoego")
    ),
    Developer(
      id = "fthomas",
      name = "Frank S. Thomas",
      email = "",
      url(s"https://github.com/fthomas")
    ),
    Developer(
      id = "mzuehlke",
      name = "Marco Zühlke",
      email = "",
      url(s"https://github.com/mzuehlke")
    )
  )
)

lazy val dockerSettings = Def.settings(
  dockerBaseImage := Option(System.getenv("DOCKER_BASE_IMAGE"))
    .getOrElse("adoptopenjdk/openjdk11:alpine"),
  dockerCommands ++= {
    val binDir = "/usr/local/bin"
    val sbtVer = sbtVersion.value
    val sbtTgz = s"sbt-$sbtVer.tgz"
    val sbtUrl = s"https://github.com/sbt/sbt/releases/download/v$sbtVer/$sbtTgz"
    val millBin = s"$binDir/mill"
    val millVer = Dependencies.millVersion.value
    val millUrl =
      s"https://github.com/lihaoyi/mill/releases/download/${millVer.split("-").head}/$millVer"
    val coursierBin = s"$binDir/coursier"
    Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "apk --no-cache add bash git ca-certificates curl maven openssh"),
      Cmd("RUN", s"wget $sbtUrl && tar -xf $sbtTgz && rm -f $sbtTgz"),
      Cmd("RUN", s"curl -L $millUrl > $millBin && chmod +x $millBin"),
      Cmd("RUN", s"curl -L https://git.io/coursier-cli > $coursierBin && chmod +x $coursierBin"),
      Cmd("RUN", s"$coursierBin install --install-dir $binDir scalafix scalafmt")
    )
  },
  Docker / packageName := s"fthomas/${name.value}",
  dockerUpdateLatest := true,
  dockerAliases ++= {
    if (!isSnapshot.value) Seq(dockerAlias.value.withTag(Option("latest-release"))) else Nil
  },
  dockerEnvVars := Map(
    "PATH" -> "/opt/docker/sbt/bin:${PATH}",
    "COURSIER_PROGRESS" -> "false"
  )
)

lazy val noPublishSettings = Def.settings(
  publish / skip := true
)

lazy val scaladocSettings = Def.settings(
  Compile / doc / scalacOptions ++= {
    val tag = s"v${version.value}"
    val tree = if (isSnapshot.value) git.gitHeadCommit.value.getOrElse(mainBranch) else tag
    Seq(
      "-doc-source-url",
      s"${scmInfo.value.get.browseUrl}/blob/$tree€{FILE_PATH}.scala",
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath
    )
  }
)

/// setting keys

lazy val checkDocs = taskKey[Unit]("")

lazy val installPlugin = taskKey[Unit]("Copies StewardPlugin.scala into global plugins directory.")
installPlugin := {
  val name = "StewardPlugin.scala"
  val source = (`sbt-plugin`.jvm / Compile / sources).value.find(_.name == name).get
  val target = file(System.getProperty("user.home")) / ".sbt" / "1.0" / "plugins" / name
  IO.copyFile(source, target)
}

lazy val moduleRootPkg = settingKey[String]("").withRank(KeyRanks.Invisible)
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
    "scalafmtCheckAll",
    "scalafmtSbtCheck",
    "unusedCompileDependenciesTest",
    "coverage",
    "test",
    "coverageReport",
    "doc",
    "docs/mdoc",
    "docs/checkDocs",
    "package",
    "packageSrc",
    "core/assembly",
    "Docker/publishLocal"
  )
)

addCommandsAlias(
  "fmt",
  Seq(
    "headerCreate",
    "scalafmtAll",
    "scalafmtSbt"
  )
)
