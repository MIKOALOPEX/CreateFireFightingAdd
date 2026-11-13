@echo off
setlocal

set "PROJECT_DIR=%~dp0"
set "MOD_VERSION=0.2.9-beta"
set "PHYSICS_ENV=Sable 2"
set "GAME_DIR=%PROJECT_DIR%run"

title Create FireFightingAdd Transition %MOD_VERSION% - %PHYSICS_ENV%

echo Starting Create FireFightingAdd Transition %MOD_VERSION% test client...
echo Physics environment: %PHYSICS_ENV%
echo Project: %PROJECT_DIR%
echo Game directory: %GAME_DIR%
echo.
echo Other mods mirror D:\CreateAdd\CreateFireFightingAdd\run\mods.
echo Use /cffa prepare_split in an old integrated world copy, then save and quit within 30 seconds.
echo Test the resulting CFFA-029-CLEANED copy in the split-mod environment, not this client.
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

call gradlew.bat runClient --console=plain
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo Client exited with error code %EXIT_CODE%.
) else (
    echo Client exited normally.
)
pause
exit /b %EXIT_CODE%
