param([string]$Project = 'dev/OL')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$pipelineJar = Join-Path $projectRoot 'asset-pipeline/target/swordol-assets.jar'
if (!(Test-Path -LiteralPath $pipelineJar)) { throw 'Build the CLI first: scripts/mvn.ps1 -pl asset-pipeline -am package' }
$pythonPath = (& python -c 'import sys; print(sys.executable)').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Python with Tkinter is required.' }
$pythonWindow = Join-Path (Split-Path $pythonPath -Parent) 'pythonw.exe'
if (!(Test-Path -LiteralPath $pythonWindow)) { $pythonWindow = $pythonPath }
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot '.tools') | Out-Null
$scriptPath = Join-Path $projectRoot 'tools/analysis/asset_bundler.py'
$projectPath = if ([IO.Path]::IsPathRooted($Project)) { $Project } else { Join-Path $projectRoot $Project }
Start-Process -FilePath $pythonWindow -ArgumentList @("`"$scriptPath`"", '--project', "`"$projectPath`"") -WorkingDirectory $projectRoot `
    -RedirectStandardOutput (Join-Path $projectRoot '.tools/asset-bundler.log') `
    -RedirectStandardError (Join-Path $projectRoot '.tools/asset-bundler.err') | Out-Null
