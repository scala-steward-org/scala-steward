# Frequently Asked Questions

## How can Scala Steward's PRs be merged automatically?

You can use [Mergify](https://mergify.io) to automatically merge Scala Steward's
pull requests. Mergify rules that does this can be found in Scala Steward's own
repository [here](https://github.com/fthomas/scala-steward/blob/master/.mergify.yml).
Mergify can also be configured to only merge patch updates (in case the version
number adheres to [Semantic Versioning](https://semver.org/)) as demonstrated
[here](https://github.com/fthomas/refined/blob/master/.mergify.yml).
