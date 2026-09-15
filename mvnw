#!/bin/sh
set -eu

MAVEN_VERSION=3.9.9
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_HOME="$BASE_DIR/.mvn/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_ZIP="$BASE_DIR/.mvn/wrapper/dists/apache-maven-$MAVEN_VERSION-bin.zip"
MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$BASE_DIR/.mvn/wrapper/dists"
  if [ ! -f "$MAVEN_ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "$MAVEN_URL" -o "$MAVEN_ZIP"
    else
      wget -q "$MAVEN_URL" -O "$MAVEN_ZIP"
    fi
  fi
  unzip -q -o "$MAVEN_ZIP" -d "$BASE_DIR/.mvn/wrapper/dists"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
