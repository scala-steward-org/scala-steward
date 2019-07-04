## Running Scala Steward

```bash
sbt stage

./modules/core/.jvm/target/universal/stage/bin/scala-steward \
  --workspace  "$STEWARD_DIR/workspace" \
  --repos-file "$STEWARD_DIR/repos.md" \
  --git-author-name "Scala steward" \
  --git-author-email ${EMAIL} \
  --vcs-api-host "https://api.github.com" \
  --vcs-login ${LOGIN} \
  --git-ask-pass "$STEWARD_DIR/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR
```

Or,

```bash
sbt docker:publishLocal

docker run -v $STEWARD_DIR:/opt/scala-steward -it scala-steward:0.1.0-SNAPSHOT \
  --workspace  "/opt/scala-steward/workspace" \
  --repos-file "/opt/scala-steward/repos.md" \
  --git-author-name "Scala steward" \
  --git-author-email ${EMAIL} \
  --vcs-api-host "https://api.github.com" \
  --vcs-login ${LOGIN} \
  --git-ask-pass "/opt/scala-steward/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR
```

If you run Scala Steward for your own private projects, you can pass additional environment variables from the command line using the `--env-var` flag as shown in the examples above. You can use this to pass any credentials required by your projects to resolve any private dependencies, e.g.:

```bash
--env-var BINTRAY_USER=username \
--env-var BINTRAY_PASS=password
```

These variables will be accessible (in sbt) to all of the projects that Scala Steward checks dependencies for.
