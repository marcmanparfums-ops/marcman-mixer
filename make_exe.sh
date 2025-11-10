#!/bin/bash

# publica ultimele modificari din proiect si creeaza executabil cu versiunea urmatoare

set -e

cd ~/Documents/MarcmanMixer

git add .
git commit -m "commit automat"
git push

git fetch --tags

latest=$(git tag --sort=-v:refname | head -1)
IFS='.' read -r major minor patch <<< "$latest"

next=$((patch + 1))

new_version="$major.$minor.$next"

git tag $new_version
git push origin $new_version

