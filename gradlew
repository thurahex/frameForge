#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo 'Wrapper jar missing: generate with installed Gradle using: gradle wrapper --gradle-version 8.13' >&2
  exit 1
fi
if [ -n "$JAVA_HOME" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi
exec "$JAVA" -Xmx64m -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
