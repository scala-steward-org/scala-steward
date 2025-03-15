# Frequently Asked Questions

## How does Scala Steward decide what version it is updating to?

Scala Steward first proposes an update to the latest patch version at the
same minor and major version. If the dependency is already on the latest patch
version, it proposes an update to the latest minor version at the same major
version. And if the dependency is already on the latest minor version, it
proposes an update to the latest major version.

Here is an example. Suppose you are depending on a library foo, which has been
published for the following versions: 1.0.0, 1.0.1, 1.0.2, 1.1.0, 1.2.0, 2.0.0,
and 3.0.0.

* If your version is 1.0.0 or 1.0.1, Scala Steward would create a PR updating it to 1.0.2
* If your version is 1.0.2 or 1.1.0, Scala Steward would create a PR updating it to 1.2.0
* If your version is 1.2.0 or 2.0.0, Scala Steward would create a PR updating it to 3.0.0

(This can be verified with [this Scastie](https://scastie.scala-lang.org/iYwxJrLWSAOElJC1gMDAOg).)

Of course, once you merge a Scala Steward PR, you've updated your version,
which can result in Scala Steward sending another PR making the next update.

## How can version updates be controlled

You can control the update of individual dependencies or of all dependencies
belonging to a group by specifying version prefixes for them:

```properties
updates.pin = [
  # Allow x.y to x.(y+1), but not to (x+1).y
  { groupId = "a.group.id", artifactId="a.name", version="x." },
  # Allow x.y.z to x.y.(z+1), but not to x.(y+1)
  { groupId = "a.group.id", artifactId="another.name", version="x.y." }


  # Fix the major version for all dependencies with this groupId
  { groupId = "a.group.id", version="x." },
]
```

Updates for `sbt` and `scalafmt` can be controlled by using the following `groupId` and `artifactId`:
```properties
{ groupId = "org.scala-sbt", artifactId = "sbt" }
{ groupId = "org.scalameta", artifactId = "scalafmt-core" }
```

Updates for the Scala 2 or Scala 3 version can be controlled by using the respective `groupId` and `artifactId`:
```properties
{ groupId = "org.scala-lang", artifactId = "scala-library" }
{ groupId = "org.scala-lang", artifactId = "scala3-library" }
```

## Can Scala Steward update multiple branches in a repository?

Yes! You can update multiple branches of a repository by adding it several times to the "repos.md" file
and a suffix with a different branch on each occurrence (`owner/repo:branch`). For example:

```md
// repos.md
- owner/repo
- owner/repo:0.1.x
- owner/repo:0.2.x
```

This configuration will update the default branch, as well as the branches `0.1.x` and `0.2.x` in the repo `owner/repo`.

## Can Scala Steward update dependencies in giter8 templates ?

Scala Steward can update versions in giter8 templates if the dependencies of the template 
are also added as dependencies of the template build.
An example is [library.g8](https://github.com/ChristopherDavenport/library.g8) ([example PR](https://github.com/ChristopherDavenport/library.g8/pull/100/files))

## Why do Scala Steward updates provide no URLs in PRs?

Scala Steward updates can only provide links to release notes, diffs and changelogs if
the `ivy.xml` file contain the `homepage` attribute or the `pom.xml` contains either
a  `scm.url` or an `url` attribute.

## How can Scala Steward's PRs be merged automatically?

You can use [Mergify](https://mergify.com) to automatically merge Scala Steward's
pull requests. Mergify rules that does this can be found in Scala Steward's own
repository [here](https://github.com/scala-steward-org/scala-steward/blob/main/.mergify.yml).
Mergify can also be configured to only merge patch updates (in case the version
number adheres to [Semantic Versioning](https://semver.org/)) as demonstrated
[here](https://github.com/fthomas/refined/blob/master/.mergify.yml).

Other examples of Mergify rules for Scala Steward can be found via
[GitHub's code search](https://github.com/search?p=6&q=%22author%3Dscala-steward%22+filename%3A.mergify.yml&type=Code).

An alternative is the [merge PR github action](https://github.com/marketplace/actions/merge-dependency-update-prs)
which you can include in a GitHub workflow to automatically merge Scala Steward's 
pull requests.

## Where can Scala Steward's PGP key be found?

Scala Steward signs commits with a PGP key. The fingerprint of that key is
774C3674392662AE645C9B8396BDF10FFAB8B6A6 and it can be found at
https://keys.openpgp.org/search?q=me@scala-steward.org.

## How can I change log levels?

If you want to switch from the standard INFO log level you can call scala steward with `-DLOG_LEVEL=DEBUG` for example: `scala-steward -DLOG_LEVEL=DEBUG  --workspace  "$STEWARD_DIR/workspace" ...` 

## Why doesn't self-hosted Scala Steward close obsolete PRs?

When a new version of a dependency is found and the PR for older version still exists, Scala Steward 
will be able to close the old PR.

If you don't observe such behaviour and new version PRs pile up alongside old ones,
make sure that the [workspace](running.md#workspace) 
folder is persisted between different runs. 

That's where Steward holds intermediate state about dependency versions and created PRs which ultimately
helps it make decisions about which PRs to close and which ones to keep open.
