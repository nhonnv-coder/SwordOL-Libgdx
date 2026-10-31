param([string]$Project = 'assets/source/demo', [string]$Map, [int]$SmokeFrames = 0, [string]$Screenshot)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $projectRoot 'resource-editor/target/swordol-editor.jar'
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Build first: scripts/mvn.ps1 -pl resource-editor -am package' }
$arguments = @('--enable-native-access=ALL-UNNAMED', "-Dswordol.editor.project=$Project")
if ($Map) { $arguments += "-Dswordol.editor.map=$Map" }
if ($SmokeFrames -gt 0) { $arguments += "-Dswordol.editor.smokeFrames=$SmokeFrames" }
if ($Screenshot) { $arguments += "-Dswordol.editor.screenshot=$Screenshot" }
$arguments += @('-jar', $jarPath)
& java @arguments
exit $LASTEXITCODE
