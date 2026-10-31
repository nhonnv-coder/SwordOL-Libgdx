param([int]$Port = 8777)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:PYTHONIOENCODING = 'utf-8'
# Start the local API/server in its own window (close it to stop), then open the browser.
Start-Process python -ArgumentList @('tools/webapp/server.py', $Port) -WorkingDirectory $projectRoot
Start-Sleep -Milliseconds 1200
Start-Process "http://127.0.0.1:$Port"
