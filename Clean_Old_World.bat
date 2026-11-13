@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0clean-world.ps1" %*
set "CLEANER_EXIT=%ERRORLEVEL%"
echo Press any key to close this window...
pause >nul
exit /b %CLEANER_EXIT%
