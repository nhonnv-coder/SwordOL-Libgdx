param([string]$AppVersion = '0.1.0', [string]$JdkHome = $env:JAVA_HOME, [switch]$Protected)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $projectRoot 'lwjgl3/target/swordol-desktop.jar'
if ($Protected) { $jarPath = Join-Path $projectRoot 'dist/protected/swordol-desktop-protected.jar' }
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Build first: scripts/mvn.ps1 verify' }
. (Join-Path $PSScriptRoot 'java-home.ps1')
$JdkHome = Resolve-SwordolJdkHome $JdkHome
$jpackagePath = if ($JdkHome) { Join-Path $JdkHome 'bin/jpackage.exe' } else { 'jpackage' }
$stagePath = Join-Path $projectRoot $(if ($Protected) { 'dist/staging-protected' } else { 'dist/staging' })
$destination = Join-Path $projectRoot $(if ($Protected) { 'dist/windows-protected' } else { 'dist/windows' })
if (Test-Path -LiteralPath (Join-Path $destination 'SwordOL')) { throw 'Package already exists. Choose a new output directory or move the previous package before rerunning.' }
New-Item -ItemType Directory -Force -Path $stagePath,$destination | Out-Null
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $stagePath 'swordol-desktop.jar')
& $jpackagePath --type app-image --name SwordOL --app-version $AppVersion --input $stagePath --main-jar swordol-desktop.jar --main-class vn.swordol.desktop.DesktopLauncher --dest $destination --java-options '--enable-native-access=ALL-UNNAMED'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
Write-Host "Portable app: $destination/SwordOL/SwordOL.exe"
