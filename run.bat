@echo off
REM Install the debug build onto a connected device and launch it.

setlocal

if "%JAVA_HOME%"=="" set JAVA_HOME=D:\app\zulu21.48.17-ca-jdk21.0.10

set "GRADLE_CMD="
if exist "%GRADLE_HOME%\bin\gradle.bat" (
    set "GRADLE_CMD=%GRADLE_HOME%\bin\gradle.bat"
) else (
    set "GRADLE_CMD=%CD%\gradlew.bat"
)

call "%GRADLE_CMD%" installDebug %*

if errorlevel 1 (
    echo [FAIL] Install failed. Check that a device or emulator is connected
    echo        and that USB debugging is enabled.
    exit /b 1
)

echo [OK] Installed. Launching Ran...

adb shell am start -n com.ran.app.debug/com.ran.app.MainActivity

endlocal