#!/usr/bin/env bash
# Build the Ran Android app and run the unit tests.
#
# Requires JDK 17+ and an Android SDK with platform 34 + build-tools 34.0.0.
#
# Set GRADLE_HOME to a Gradle 8.9+ installation to bypass the wrapper, e.g.
#   export GRADLE_HOME=/d/app/gradle-8.9
# Leaving it unset uses ./gradlew, which downloads the distribution on first run.

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

if [ ! -f "local.properties" ]; then
    echo "[WARN] local.properties not found. Create it with: sdk.dir=your-sdk-path"
fi

"$GRADLE" clean testDebugUnitTest assembleDebug assembleRelease "$@"

echo
echo "[OK] Build finished. Unit tests ran as part of testDebugUnitTest."
echo "DebugAPK:   app/build/outputs/apk/debug/app-debug.apk"
echo "ReleaseAPK: app/build/outputs/apk/release/app-release.apk"