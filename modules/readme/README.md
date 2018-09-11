# scala-steward
[![Build Status](https://travis-ci.org/fthomas/scala-steward.svg?branch=master)](https://travis-ci.org/fthomas/scala-steward)
[![codecov](https://codecov.io/gh/fthomas/scala-steward/branch/master/graph/badge.svg)](https://codecov.io/gh/fthomas/scala-steward)
[![Join the chat at https://gitter.im/fthomas/scala-steward](https://badges.gitter.im/fthomas/scala-steward.svg)](https://gitter.im/fthomas/scala-steward?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**scala-steward** is a simple robot that helps you keeping library and plugin
dependencies up-to-date.

## Quick start guide

Open a pull request that adds the repo of your Scala project to
[repos.md](https://github.com/fthomas/scala-steward/edit/master/repos.md).
Once that PR is merged, [@scala-steward][@scala-steward] will check every
few hours for updates of libraries and plugins in your project and will open
PRs against your repo if it can figure out where version numbers need to be
updated.

Here are the pull requests [@scala-steward][@scala-steward] has created so far:

https://github.com/pulls?q=is%3Apr+author%3Ascala-steward

## License

**scala-steward** is licensed under the Apache License, Version 2.0, available at
http://www.apache.org/licenses/LICENSE-2.0 and also in the
[LICENSE](https://github.com/fthomas/status-page/blob/master/LICENSE) file.

## Credit

The awesome [sbt-updates][sbt-updates] is used to determine dependency updates.

The profile picture of [@scala-steward][@scala-steward] was taken from
https://pixabay.com/en/robot-flower-technology-future-1214536/

[@scala-steward]: https://github.com/scala-steward
[sbt-updates]: https://github.com/rtimush/sbt-updates
