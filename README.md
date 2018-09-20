# scala-steward
[![Build Status](https://travis-ci.org/fthomas/scala-steward.svg?branch=master)](https://travis-ci.org/fthomas/scala-steward)
[![codecov](https://codecov.io/gh/fthomas/scala-steward/branch/master/graph/badge.svg)](https://codecov.io/gh/fthomas/scala-steward)
[![Join the chat at https://gitter.im/fthomas/scala-steward](https://badges.gitter.im/fthomas/scala-steward.svg)](https://gitter.im/fthomas/scala-steward?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4573461025c642daa4128b659ee54fc9)](https://www.codacy.com/app/fthomas/scala-steward?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=fthomas/scala-steward&amp;utm_campaign=Badge_Grade)

**scala-steward** is a simple robot that helps you keeping library and plugin
dependencies up-to-date.

## Quick start guide

Open a pull request that adds the repo of your Scala project to
[repos.md](https://github.com/fthomas/scala-steward/edit/master/repos.md).
Once that PR is merged, [**@scala-steward**][@scala-steward] will check every
few hours for updates of libraries and plugins in your project and will open
PRs against your repo if it can figure out where version numbers need to be
updated.

Here are the pull requests [**@scala-steward**][@scala-steward] has created so far:

* [Created pull requests](https://github.com/pulls?q=author%3Ascala-steward+is%3Apr)
* [Merged pull requests]( https://github.com/pulls?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc)

## Credit

The awesome [sbt-updates][sbt-updates] plugin is used to determine dependency updates.

The profile picture of [**@scala-steward**][@scala-steward] was taken from
https://pixabay.com/en/robot-flower-technology-future-1214536/

[@scala-steward]: https://github.com/scala-steward
[sbt-updates]: https://github.com/rtimush/sbt-updates

## License

**scala-steward** is licensed under the Apache License, Version 2.0, available at
http://www.apache.org/licenses/LICENSE-2.0 and also in the
[LICENSE](https://github.com/fthomas/status-page/blob/master/LICENSE) file.
