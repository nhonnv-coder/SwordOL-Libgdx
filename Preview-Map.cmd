@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-map-preview.ps1"
if errorlevel 1 pause
