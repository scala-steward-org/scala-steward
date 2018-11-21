#!/bin/sh

set -ex

export PATH="/opt/jre/current/bin:$PATH"
SCRIPT=$(readlink -f "$0")
STEWARD_DIR=$(dirname "$SCRIPT")/..
cd "$STEWARD_DIR"
git pull
export SBT_OPTS="-Xms256M -Xmx768M"
sbt -no-colors ";clean ;core/assembly"
JAR=$(find -name "*assembly*.jar" | head -n1)

LOGIN="scala-steward"
WORKSPACE="$HOME/code/$LOGIN/workspace"
java -jar ${JAR} \
  --workspace ${WORKSPACE} \
  --repos-file "$WORKSPACE/../repos.md" \
  --git-author-name "Scala steward" \
  --git-author-email "me@$LOGIN.org" \
  --github-api-host "https://api.github.com" \
  --github-login ${LOGIN} \
  --git-ask-pass "$HOME/.github/askpass/$LOGIN.sh"
