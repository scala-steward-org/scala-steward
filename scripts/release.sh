#!/bin/sh

VERSION="$1"
if [ -z "$VERSION" ]; then
    exit 1
fi

git tag -a -s v$VERSION -m "Releasing $VERSION"
git push --tags

gren changelog --generate --override

git commit -a -m "Update changelog for version $VERSION"
git push
