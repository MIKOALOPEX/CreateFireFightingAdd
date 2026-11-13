@echo off
setlocal

set "PROJECT_DIR=%~dp0"
set "PLAYER_NAME=VED"
set "MOD_VERSION=0.2.9-beta"
set "PHYSICS_ENV=Sable 2"

title Create FireFightingAdd Transition %MOD_VERSION% - %PHYSICS_ENV% - %PLAYER_NAME%

echo Starting Create FireFightingAdd Transition %MOD_VERSION% test client as %PLAYER_NAME%...
echo Physics environment: %PHYSICS_ENV%
echo Project: %PROJECT_DIR%
echo Game directory: %PROJECT_DIR%run
echo.

cd /d "%PROJECT_DIR%"
if errorlevel 1 (
    echo Failed to enter project directory.
    pause
    exit /b 1
)

if not exist "gradlew.bat" (
    echo gradlew.bat was not found in %PROJECT_DIR%.
    pause
    exit /b 1
)

call gradlew.bat --console=plain runVedClient %*
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo Minecraft %PLAYER_NAME% client exited with code %EXIT_CODE%.
) else (
    echo Minecraft %PLAYER_NAME% client exited normally.
)
pause
exit /b %EXIT_CODE%
