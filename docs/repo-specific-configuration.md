# Repository-specific configuration

You can add `<YOUR_REPO>/.scala-steward.conf` to configure how Scala Steward updates your repository.

```properties
# The only dependencies which match the given pattern are updated.
# Each pattern must have `groupId`, and may have `artifactId` and `version`.
# The version is treated as a prefix of the new version, unless it starts with
# ".*" then it is treated as a suffix.
# Defaults to empty `[]` which mean Scala Steward will update all dependencies.
updates.allow  = [ { groupId = "com.example" } ]

# The dependencies which match the given pattern are NOT updated.
# Each pattern must have `groupId`, and may have `artifactId` and `version`.
# The version is treated as a prefix of the new version, unless it starts with
# ".*" then it is treated as a suffix.
# Defaults to empty `[]` which mean Scala Steward will not ignore dependencies.
updates.ignore = [ { groupId = "org.acme", artifactId="foo", version = "1.0" } ]

# If set, Scala Steward will only attempt to create or update `n` PRs.
# Useful if running frequently and/or CI build are costly
# Default: None
updates.limit = 5

# If "on-conflicts", Scala Steward will update the PR it created to resolve conflicts as
# long as you don't change it yourself.
# If "always", Scala Steward will always update the PR it created as long as
# you don't change it yourself.
# If "never", Scala Steward will never update the PR
# Default: "on-conflicts"
updatePullRequests = "always" | "on-conflicts" | "never"
```

## Ignore lines

Though `updates.ignores` offers granular configuration to exclude dependencies from update, Scala Steward also recognizes markers in file to ignore lines.

Dependencies in lines between `// scala-steward:off` and `// scala-steward:on` are not updated.

```scala
libraryDependencies ++= Seq(
  // scala-steward:off
  "com.github.pathikrit" %% "better-files" % "3.8.0",
  "com.olegpy" %% "better-monadic-for" % "0.3.1",
  // scala-steward:on 
  "org.typelevel" %% "cats-effect" % "1.3.1",  // This and subsequent will get updated
  "org.typelevel" %% "cats-kernel-laws" % "1.6.1"
)
```

Also, the line ends with `// scala-steward:off` is not updated solely.

```scala
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.0", // scala-steward:off
  "com.typesafe.akka" %% "akka-testkit" % "2.5.0", // This and subsequent will get updated
)
```
