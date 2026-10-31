@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\collect-server-data.ps1"
pause
