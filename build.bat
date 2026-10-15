@echo off
REM Build the Ran Android app.
REM Requires JDK 17+ and an Android SDK with platform 34 + build-tools 34.0.0.
REM
REM Set GRADLE_HOME to a Gradle 8.9+ installation, or leave it unset to use
REM ./gradlew.bat. Example: set GRADLE_HOME=D:\app\gradle-8.9

setlocal

if "%JAVA_HOME%"=="" set JAVA_HOME=D:\app\zulu21.48.17-ca-jdk21.0.10

set "GRADLE_CMD="
if exist "%GRADLE_HOME%\bin\gradle.bat" (
    set "GRADLE_CMD=%GRADLE_HOME%\bin\gradle.bat"
) else (
    set "GRADLE_CMD=%CD%\gradlew.bat"
)

if not exist "local.properties" (
    echo [WARN] local.properties not found. Create it with: sdk.dir=your-sdk-path
)

call "%GRADLE_CMD%" clean testDebugUnitTest assembleDebug assembleRelease %*

if errorlevel 1 (
    echo [FAIL] Build failed.
    exit /b 1
)

echo.
echo [OK] Build finished. Unit tests ran as part of testDebugUnitTest.
echo DebugAPK:   app\build\outputs\apk\debug\app-debug.apk
echo ReleaseAPK: app\build\outputs\apk\release\app-release.apk

endlocal