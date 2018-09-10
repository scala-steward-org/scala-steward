#!/bin/sh

SCRIPT=$(readlink -f "$0")
STEWARD_DIR=$(dirname "$SCRIPT")
cd "$STEWARD_DIR"
git pull
sbt core/run > steward.log
