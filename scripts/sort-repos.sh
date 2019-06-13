#!/bin/sh

sort --ignore-case --version-sort -o repos.md repos.md
git commit -m "Sort repos" repos.md
