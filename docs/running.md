## Running Scala Steward

```bash
sbt stage

./modules/core/.jvm/target/universal/stage/bin/scala-steward \
  --workspace  "$STEWARD_DIR/workspace" \
  --repos-file "$STEWARD_DIR/repos.md" \
  --git-author-email ${EMAIL} \
  --vcs-api-host "https://api.github.com" \
  --vcs-login ${LOGIN} \
  --git-ask-pass "$STEWARD_DIR/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR
```

> If [Firejail](https://firejail.wordpress.com/) is not available locally, the option `--disable-sandbox` can be used (not recommended for production environment).

Or as a [Docker](https://www.docker.com/) container:

```bash
sbt docker:publishLocal

docker run -v $STEWARD_DIR:/opt/scala-steward -it fthomas/scala-steward:latest \
  --workspace  "/opt/scala-steward/workspace" \
  --repos-file "/opt/scala-steward/repos.md" \
  --git-author-email ${EMAIL} \
  --vcs-api-host "https://api.github.com" \
  --vcs-login ${LOGIN} \
  --git-ask-pass "/opt/scala-steward/.github/askpass/$LOGIN.sh" \
  --sign-commits \
  --env-var FOO=BAR
```

The [`git-ask-pass` option](https://git-scm.com/docs/gitcredentials) must specify an executable file (script) that returns (on the stdout),

- either the plain text password corresponding to the configured `${LOGIN}`,
- or (recommended) an authentication token corresponding to `${LOGIN}` (with appropriate permissions to watch the repositories; e.g. [Create a personal access token](https://help.github.com/en/articles/creating-a-personal-access-token-for-the-command-line) for GitHub).

**Note about git-ask-pass option**: The provided script must start with a valid shebang like `#!/bin/sh`, see issue [#1374](/../../issues/1374)


You can also provide a `--scalafix-migrations` option with the path to a file containing scalafix migrations.
More information can be found [here][migrations]

### Private repositories

If you run Scala Steward for your own private projects, the option `--do-not-fork` can be required, not to fork.
Instead it will create pull requests directly on the private repository (as soon as the `${LOGIN}` can).

#### Credentials using environment variables

It can also be useful to pass additional environment variables from the command line using the `--env-var` flag as shown in the examples above. You can use this to pass any credentials required by your projects to resolve any private dependencies, e.g.:

```bash
--env-var BINTRAY_USER=username \
--env-var BINTRAY_PASS=password
```

These variables will be accessible (in sbt) to all of the projects that Scala Steward checks dependencies for.

#### Credentials using a credentials.sbt file

If your projects require credentials, you can also provide global credentials in the `$HOME/.sbt/1.0/credentials.sbt` file. 
The file should contain a single line: `credentials += Credentials("Some Nexus Repository Manager", "my.artifact.repo.net", "admin", "admin123")`.

### Running locally from sbt

#### Sample run for Gitlab

```
sbt
project core
run --disable-sandbox --do-not-fork --workspace "/path/workspace" --repos-file "/path/repos.md" --git-ask-pass "/path/pass.sh" --git-author-email "email@example.org" --vcs-type "gitlab" --vcs-api-host "https://gitlab.com/api/v4/" --vcs-login "gitlab.steward"
```


#### Running on Docker for Bitbucket

* Create a file `repos.md` that will be injected into the container as as volume.
* Create a file `run.sh` with this content:

```
echo "#!/bin/sh"                  >> pass.sh  
echo "echo '$BITBUCKET_PASSWORD'" >> pass.sh

chmod +x pass.sh

docker run -v $PWD:/opt/scala-steward \
    -v ~/.sbt/:/root/.sbt \
    -it fthomas/scala-steward:latest \
    --env-var LOG_LEVEL=TRACE \
    --do-not-fork \
    --workspace "/opt/scala-steward/workspace" \
    --repos-file "/opt/scala-steward/repos.md" \
    --git-ask-pass "/opt/scala-steward/pass.sh" \
    --git-author-email "myemail@company.xyz" \
    --vcs-type "bitbucket" \
    --vcs-api-host "https://api.bitbucket.org/2.0" \
    --vcs-login "$BITBUCKET_USERNAME"
    
```

* Run it from a CI tool or manually using with this command:

`BITBUCKET_USERNAME=<myuser> BITBUCKET_PASSWORD=<mypass> ./run.sh`

[migrations]: https://github.com/fthomas/scala-steward/blob/master/docs/scalafix-migrations.md

### Running On-premise (GitHub Enterprise)

There is an article on how they run Scala Steward on-premise at Avast:
* [Running Scala Steward On-premise](https://engineering.avast.io/running-scala-steward-on-premise)
