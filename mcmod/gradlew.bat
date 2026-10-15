@echo off
setlocal
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set VERSION=8.10.1
set DIST_URL=https://services.gradle.org/distributions
set GRADLE_ZIP=%GRADLE_USER_HOME%\wrapper\dists\gradle-%VERSION%-bin.zip
set GRADLE_EXE=%GRADLE_USER_HOME%\wrapper\dists\gradle-%VERSION%\bin\gradle.bat

if not exist "%GRADLE_EXE%" (
    echo Downloading Gradle %VERSION%
    if not exist "%GRADLE_USER_HOME%\wrapper\dists\" mkdir "%GRADLE_USER_HOME%\wrapper\dists"
    powershell -command "Invoke-WebRequest -Uri '%DIST_URL%\gradle-%VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
    powershell -command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%GRADLE_USER_HOME%\wrapper\dists'"
)

call "%GRADLE_EXE%" %*
endlocal