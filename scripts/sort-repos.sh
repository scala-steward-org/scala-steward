#!/bin/sh

sort -o repos.md repos.md
git commit -m "Sort repos" repos.md
