$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$pythonPath = (& python -c 'import sys; print(sys.executable)').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Python with Pillow and Tkinter is required.' }
$pythonWindow = Join-Path (Split-Path $pythonPath -Parent) 'pythonw.exe'
if (!(Test-Path -LiteralPath $pythonWindow)) { $pythonWindow = $pythonPath }
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot '.tools') | Out-Null
$scriptPath = Join-Path $projectRoot 'tools/analysis/data_manager.py'
Start-Process -FilePath $pythonWindow -ArgumentList @("`"$scriptPath`"") -WorkingDirectory $projectRoot `
    -RedirectStandardOutput (Join-Path $projectRoot '.tools/data-manager.log') `
    -RedirectStandardError (Join-Path $projectRoot '.tools/data-manager.err') | Out-Null
