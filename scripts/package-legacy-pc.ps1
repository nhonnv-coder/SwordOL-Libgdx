param([string]$Destination = '', [string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$Destination) { $Destination = Join-Path $projectRoot 'dist/SwordOL-PC-Capture' }
$Destination = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $Destination) { throw "Destination exists: $Destination. Choose a new directory to preserve captures." }
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'mvn.ps1') -o -pl legacy-capture package
if ($LASTEXITCODE -ne 0) { throw 'Capture agent build failed.' }
. (Join-Path $PSScriptRoot 'java-home.ps1')
$JdkHome = Resolve-SwordolJdkHome $JdkHome
New-Item -ItemType Directory -Force -Path (Join-Path $Destination 'app'),(Join-Path $Destination 'scripts') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/files/Emulator.jar'),(Join-Path $projectRoot 'docs/files/VoLamHoiTu.jar'),(Join-Path $projectRoot 'legacy-capture/target/swordol-capture.jar') -Destination (Join-Path $Destination 'app')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'run-legacy-pc.ps1') -Destination (Join-Path $Destination 'scripts')
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/legacy-pc.md') -Destination (Join-Path $Destination 'HUONG-DAN.md')
& (Join-Path $JdkHome 'bin/jlink.exe') --add-modules java.base,java.desktop,java.logging,java.xml,java.instrument,jdk.unsupported,jdk.charsets,jdk.zipfs --strip-debug --no-header-files --no-man-pages --output (Join-Path $Destination 'runtime')
if ($LASTEXITCODE -ne 0) { throw 'Runtime packaging failed.' }
@'
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-legacy-pc.ps1"
if errorlevel 1 pause
'@ | Set-Content -LiteralPath (Join-Path $Destination 'Chay-Game.cmd') -Encoding ASCII
@'
@echo off
if not exist "%~dp0captures" mkdir "%~dp0captures"
start "" "%~dp0captures"
'@ | Set-Content -LiteralPath (Join-Path $Destination 'Mo-Du-Lieu.cmd') -Encoding ASCII
Write-Host "Ready: $Destination/Chay-Game.cmd"
