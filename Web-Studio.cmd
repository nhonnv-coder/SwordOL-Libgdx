@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-web-studio.ps1"
if errorlevel 1 pause
