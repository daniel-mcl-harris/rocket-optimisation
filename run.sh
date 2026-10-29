#!/usr/bin/env bash
# Unix equivalent of run.ps1. Maven and Java errors are left on stderr,
# and a non-zero exit from either command stops the script with that code.
set -euo pipefail

echo "Building project..."
mvn compile dependency:copy-dependencies -q

echo "Running application..."
echo

skip_warning() {
    case "$1" in
        *"WARNING: package sun.misc not in java.base"*) return 0 ;;
        *"WARNING: A terminally deprecated"*) return 0 ;;
        *"WARNING: Please consider reporting"*) return 0 ;;
        *"WARNING: sun.misc.Unsafe"*) return 0 ;;
        *"WARNING: Final field"*) return 0 ;;
        *"WARNING: Use --enable-final-field-mutation"*) return 0 ;;
        *"WARNING: Mutating final fields"*) return 0 ;;
        *) return 1 ;;
    esac
}

java --add-opens java.base/java.lang=ALL-UNNAMED \
    -cp "target/classes:target/dependency/*" \
    com.danielh.App "$@" 2>&1 | while IFS= read -r line || [ -n "$line" ]; do
    if skip_warning "$line"; then
        continue
    fi
    printf '%s\n' "$line"
done
