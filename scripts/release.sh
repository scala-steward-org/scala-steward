#!/bin/sh

VERSION="$1"
if [ -z "$VERSION" ]; then
    exit 1
fi

git tag -a -s v$VERSION -m "Releasing $VERSION"
git push --tags
