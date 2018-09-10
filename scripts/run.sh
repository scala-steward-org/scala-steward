#!/bin/sh

SCRIPT=$(readlink -f "$0")
STEWARD_DIR=$(dirname "$SCRIPT")/..
cd "$STEWARD_DIR"
git pull
sbt ";clean ;core/assembly"
JAR=$(find -name "*assembly*.jar" | head -n1)
java -jar $JAR > steward.log
