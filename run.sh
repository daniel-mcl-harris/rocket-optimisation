#!/usr/bin/env bash
# Unix equivalent of run.ps1. Maven and Java errors are left on the terminal,
# and a non-zero exit from either command is the script's exit code.
# Java is started attached to this terminal. Do not pipe or redirect it:
# a pipe clears System.console() and the progress line can no longer update
# in place. JDK warnings are suppressed with JVM flags instead.
set -euo pipefail

echo "Building project..."
mvn compile dependency:copy-dependencies -q

echo "Running application..."
echo

set +e
java --enable-native-access=ALL-UNNAMED \
    --sun-misc-unsafe-memory-access=allow \
    --enable-final-field-mutation=ALL-UNNAMED \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    -cp "target/classes:target/dependency/*" \
    com.danielh.App "$@"
status=$?
exit "$status"
