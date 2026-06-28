#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building Javadoc ==="
./gradlew javadoc

echo "=== Building MkDocs site (Docker) ==="
docker run --rm -v "$(pwd):/docs" squidfunk/mkdocs-material build

echo "=== Copying Javadoc into site ==="
mkdir -p site/javadoc
rm -rf site/javadoc/*
cp -r build/docs/javadoc/. site/javadoc/

echo "=== Done ==="
echo "Open site/index.html in your browser."
