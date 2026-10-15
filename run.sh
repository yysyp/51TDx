#!/usr/bin/env bash
# Install the debug build onto a connected device and launch it.
#
# Set GRADLE_HOME to a Gradle 8.9+ installation to bypass the wrapper.

set -euo pipefail

: "${JAVA_HOME:=D:/app/zulu21.48.17-ca-jdk21.0.10}"
export JAVA_HOME

if [ -n "${GRADLE_HOME:-}" ] && [ -x "$GRADLE_HOME/bin/gradle" ]; then
    GRADLE="$GRADLE_HOME/bin/gradle"
elif [ -x ./gradlew ]; then
    GRADLE=./gradlew
else
    echo "[FAIL] No Gradle found. Set GRADLE_HOME to a Gradle 8.9+ install." >&2
    exit 1
fi

"$GRADLE" installDebug "$@"

echo "[OK] Installed. Launching Ran..."
adb shell am start -n com.ran.app.debug/com.ran.app.MainActivity