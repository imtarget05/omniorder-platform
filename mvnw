#!/usr/bin/env bash
set -e

MAVEN_PROJECTBASEDIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

if [ ! -r "$MAVEN_WRAPPER_JAR" ]; then
  echo "Maven Wrapper JAR not found: $MAVEN_WRAPPER_JAR" >&2
  exit 1
fi

exec java ${MAVEN_OPTS:-} \
  -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
  -classpath "$MAVEN_WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
