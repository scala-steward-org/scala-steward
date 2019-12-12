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

Of course, once you merge a Scala Steward PR, you've updated your version,
which can result in Scala Steward sending another PR making the next update.

## How can Scala Steward's PRs be merged automatically?

You can use [Mergify](https://mergify.io) to automatically merge Scala Steward's
pull requests. Mergify rules that does this can be found in Scala Steward's own
repository [here](https://github.com/fthomas/scala-steward/blob/master/.mergify.yml).
Mergify can also be configured to only merge patch updates (in case the version
number adheres to [Semantic Versioning](https://semver.org/)) as demonstrated
[here](https://github.com/fthomas/refined/blob/master/.mergify.yml).

Other examples of Mergify rules for Scala Steward can be found via
[GitHub's code search](https://github.com/search?p=6&q=%22author%3Dscala-steward%22+filename%3A.mergify.yml&type=Code).

## Where can Scala Steward's PGP key be found?

Scala Steward signs commits with a PGP key. The fingerprint of that key is
774C3674392662AE645C9B8396BDF10FFAB8B6A6 and it can be found at
https://keys.openpgp.org/search?q=me@scala-steward.org.
