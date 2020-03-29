# Repository-specific configuration

You can add `<YOUR_REPO>/.scala-steward.conf` to configure how Scala Steward updates your repository.

```properties
# pullRequests.frequency allows to control how often or when Scala Steward
# is allowed to create pull requests.
#
# Possible values:
#   @asap
#     PRs are created without delay.
#
#   @daily | @weekly | @monthly
#     PRs are created at least 1 day | 7 days | 30 days after the last PR.
#
#   <CRON expression>
#     PRs are created roughly according to the given CRON expression.
#
#     CRON expressions consist of five fields:
#     minutes, hour of day, day of month, month, and day of week.
#
#     See https://www.alonsodomin.me/cron4s/userguide/index.html#parsing for
#     more information about the CRON expressions that are supported.
#
#     Note that the date parts of the CRON expression are matched exactly
#     while the the time parts are only used to abide to the frequency of
#     the given expression.
#
# Default: @asap
#
#pullRequests.frequency = "0 0 ? * 3" # every thursday on midnight
pullRequests.frequency = "@weekly"

# Only these dependencies which match the given patterns are updated.
#
# Each pattern must have `groupId`, and may have `artifactId` and `version`.
# Defaults to empty `[]` which mean Scala Steward will update all dependencies.
updates.allow  = [ { groupId = "com.example" } ]

# The dependencies which match the given version pattern are updated.
# Dependencies that are not listed will be updated.
#
# Each pattern must have `groupId`, `version` and optional `artifactId`.
# Defaults to empty `[]` which mean Scala Steward will update all dependencies.
# the following example will allow to update foo when version is 1.1.x
updates.pin  = [ { groupId = "com.example", artifactId="foo", version = "1.1." } ]

# The dependencies which match the given pattern are NOT updated.
#
# Each pattern must have `groupId`, and may have `artifactId` and `version`.
# Defaults to empty `[]` which mean Scala Steward will not ignore dependencies.
updates.ignore = [ { groupId = "org.acme", artifactId="foo", version = "1.0" } ]

# If set, Scala Steward will only attempt to create or update `n` PRs.
# Useful if running frequently and/or CI build are costly
# Default: None
updates.limit = 5

# By default, Scala Steward does not update scala version since its tricky, error-prone
# and results in bad PRs and/or failed builds
# If set to true, Scala Steward will attempt to update the scala version
# Since this feature is experimental, the default is set to false
# Default: false
updates.includeScala = true

# If "on-conflicts", Scala Steward will update the PR it created to resolve conflicts as
# long as you don't change it yourself.
# If "always", Scala Steward will always update the PR it created as long as
# you don't change it yourself.
# If "never", Scala Steward will never update the PR
# Default: "on-conflicts"
updatePullRequests = "always" | "on-conflicts" | "never"

# If set, Scala Steward will use this message template for the commit messages and PR titles.
# Supported variables: ${artifactName}, ${currentVersion}, ${nextVersion} and ${default}
# Default: "${default}" which is equivalent to "Update ${artifactName} to ${nextVersion}" 
commits.message = "Update ${artifactName} from ${currentVersion} to ${nextVersion}"
```

The version information given in the patterns above can be in two formats:
1. just a `version` field that is treated as a prefix of the version
2. a structure consisting of `prefix` and / or `suffix` that are matched against the beginning or the end of the version

```properties
version = "1.1."
version = { prefix = "1.1." }
version = { suffix = "jre8" }
version = { prefix = "1.1.", suffix = "jre8" }
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
