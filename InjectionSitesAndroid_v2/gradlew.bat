@echo off
rem Text-only Gradle bootstrapper. It replaces the binary Gradle Wrapper JAR
rem and downloads the pinned Gradle distribution on first use.
setlocal

set "GRADLE_VERSION=8.7"
set "DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "INSTALL_DIR=%GRADLE_USER_HOME%\bootstrap\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%INSTALL_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
set "ARCHIVE=%INSTALL_DIR%\gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_BIN%" goto runGradle
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

if not exist "%ARCHIVE%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%DIST_URL%' -OutFile '%ARCHIVE%'"
  if errorlevel 1 exit /b %errorlevel%
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%ARCHIVE%' -DestinationPath '%INSTALL_DIR%'"
if errorlevel 1 exit /b %errorlevel%
del /q "%ARCHIVE%"

:runGradle
call "%GRADLE_BIN%" %*
exit /b %errorlevel%
