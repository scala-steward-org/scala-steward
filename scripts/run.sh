#!/bin/bash -l

set -ex

SCRIPT=$(readlink -f "$0")
STEWARD_DIR=$(dirname $(dirname "$SCRIPT"))

cd "$STEWARD_DIR"
git pull
sbt -no-colors ";clean ;core/assembly"
JAR=$(find modules/ -name "scala-steward-assembly*.jar" | head -n1)

# Don't start if we can't reach Maven Central.
curl -s --head --fail https://repo1.maven.org/maven2/

REPOS_GITHUB="$STEWARD_DIR/repos-github.md"
curl -s -o "$REPOS_GITHUB" https://raw.githubusercontent.com/scala-steward-org/repos/master/repos-github.md

REPOS_GITLAB="$STEWARD_DIR/repos-gitlab.md"
curl -s -o "$REPOS_GITLAB" https://raw.githubusercontent.com/scala-steward-org/repos/master/repos-gitlab.md

LOGIN="scala-steward"

COMMON_ARGS=(
  --workspace "$STEWARD_DIR/workspace"
  --git-author-email "me@$LOGIN.org"
  --vcs-login "$LOGIN"
  --ignore-opts-files
  --env-var "SBT_OPTS=-Xmx2048m -Xss8m -XX:MaxMetaspaceSize=512m"
  --sign-commits
  --cache-ttl 6hours
  --process-timeout 20min
  --whitelist $HOME/.cache/coursier
  --whitelist $HOME/.cache/JNA
  --whitelist $HOME/.ivy2
  --whitelist $HOME/.sbt
  --whitelist $HOME/.scio-ideaPluginIC
  --whitelist $HOME/.tagless-redux-ijextPluginIC
  --whitelist $JAVA_HOME
  --read-only $JAVA_HOME
)

GITHUB_ARGS=(
  --git-ask-pass "$HOME/.github/askpass/$LOGIN.sh"
  --repos-file "$REPOS_GITHUB"
)

GITLAB_ARGS=(
  --vcs-type gitlab
  --vcs-api-host "https://gitlab.com/api/v4"
  --git-ask-pass "$HOME/.gitlab/askpass/$LOGIN.sh"
  --repos-file "$REPOS_GITLAB"
)

PIDFILE="$STEWARD_DIR/scala-steward.pid"
JAVA=$(which java)
JAVA_ARGS="-DROOT_LOG_LEVEL=INFO -DLOG_LEVEL=INFO"

/sbin/start-stop-daemon --start --make-pidfile --pidfile $PIDFILE --chdir "$STEWARD_DIR" --exec "$JAVA" \
 -- $JAVA_ARGS -jar ${JAR} "${COMMON_ARGS[@]}" "${GITHUB_ARGS[@]}" || true

/sbin/start-stop-daemon --start --make-pidfile --pidfile $PIDFILE --chdir "$STEWARD_DIR" --exec "$JAVA" \
 -- $JAVA_ARGS -jar ${JAR} "${COMMON_ARGS[@]}" "${GITLAB_ARGS[@]}" || true
