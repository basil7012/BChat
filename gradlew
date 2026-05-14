#!/bin/sh
# More robust minimal gradlew script
DIR=$(dirname "$0")
CLASSPATH="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$CLASSPATH" ]; then
    echo "Error: gradle-wrapper.jar not found at $CLASSPATH"
    exit 1
fi
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
