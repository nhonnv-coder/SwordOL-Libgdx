@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scriptsun-asset-bundler.ps1"
if errorlevel 1 pause
