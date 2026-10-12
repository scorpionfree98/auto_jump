#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JUNIT_JAR:?Set JUNIT_JAR to junit-4.13.2.jar}"
: "${HAMCREST_JAR:?Set HAMCREST_JAR to hamcrest-core-1.3.jar}"
jiojio_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
jiojio_javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
mkdir -p build-checks/classes
"$jiojio_javac" -encoding UTF-8 -d build-checks/classes -cp "$JUNIT_JAR" \
  app/src/main/java/cn/local/jiojio/Position.java \
  app/src/main/java/cn/local/jiojio/NetworkProbe.java \
  app/src/test/java/cn/local/jiojio/*.java core-tests/src/test/java/cn/local/jiojio/*.java
"$jiojio_java" -cp "build-checks/classes:core-tests/src/test/resources:$JUNIT_JAR:$HAMCREST_JAR" \
  org.junit.runner.JUnitCore cn.local.jiojio.CoreTest cn.local.jiojio.NetworkProbeIntegrationTest
