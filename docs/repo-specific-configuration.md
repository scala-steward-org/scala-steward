# Repository-specific configuration

You can add `<YOUR_REPO>/.scala-steward.conf` to configure how Scala Steward updates your repository.

```properties
# The only dependencies which match the given pattern are updated.
# Each pattern must have `groupId`, and may have `artifactId` and `version`.
# Defaults to empty `[]` which mean Scala Steward will update all dependencies.
updates.allow  = [ { groupId = "com.example" } ]

# The dependencies which match the given pattern are NOT updated.
# Each pattern must have `groupId`, and may have `artifactId` and `version`.
# Defaults to empty `[]` which mean Scala Steward will not ignore dependencies.
updates.ignore = [ { groupId = "org.acme", artifactId="foo", version = "1.0" } ]

# If true, Scala Steward will update the PR it created to resolve conflicts as
# long as you don't change it yourself.
# Default: true
updatePullRequests = true
```
