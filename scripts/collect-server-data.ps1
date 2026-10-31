$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
& python (Join-Path $projectRoot 'tools/analysis/collect_server_data.py')
if ($LASTEXITCODE -ne 0) { throw 'Server data collection failed; previous report generations remain available.' }
Write-Host "Report: $projectRoot/dev/server-data/summary.md"
