param(
    [int]$SmokeFrames = 0,
    [string]$Screenshot = '',
    # The decoded original image set the world renderer draws tiles from: a .swb bundle or
    # a directory of <id>.png files. Without it the map reports its tiles as missing.
    [string]$LegacyAssets = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $projectRoot 'lwjgl3/target/swordol-desktop.jar'
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Build first: scripts/mvn.ps1 -o verify' }
. (Join-Path $PSScriptRoot 'java-home.ps1')
$javaHome = Resolve-SwordolJdkHome $env:JAVA_HOME
$javaPath = Join-Path $javaHome 'bin/java.exe'
$javaArgs = @('--enable-native-access=ALL-UNNAMED')
if ($SmokeFrames -gt 0) { $javaArgs += "-Dswordol.smokeFrames=$SmokeFrames" }
if ($Screenshot) { $javaArgs += "-Dswordol.screenshot=$([IO.Path]::GetFullPath($Screenshot))" }
if (!$LegacyAssets) {
    $default = Join-Path $projectRoot 'assets/source/OL.swb'
    $project = Join-Path $projectRoot 'dev/OL'
    # Auto-build the game bundle when the decoded project exists and the bundle is missing or stale.
    if (Test-Path -LiteralPath (Join-Path $project 'registry.json')) {
        $pipelineJar = Join-Path $projectRoot 'asset-pipeline/target/swordol-assets.jar'
        $stale = !(Test-Path -LiteralPath $default) -or
            ((Get-ChildItem -LiteralPath $project -Recurse -File | Measure-Object -Property LastWriteTimeUtc -Maximum).Maximum -gt (Get-Item -LiteralPath $default).LastWriteTimeUtc)
        if ($stale -and (Test-Path -LiteralPath $pipelineJar)) {
            & $javaPath '-jar' $pipelineJar 'build' $project $default
            if ($LASTEXITCODE -ne 0) { throw 'Asset bundle build failed' }
        }
    }
    if (Test-Path -LiteralPath $default) { $LegacyAssets = $default }
}
if ($LegacyAssets) { $javaArgs += "-Dswordol.legacyAssets=$([IO.Path]::GetFullPath($LegacyAssets))" }
$javaArgs += @('-jar', $jarPath)
Push-Location $projectRoot
try {
    & $javaPath @javaArgs
    $result = $LASTEXITCODE
} finally { Pop-Location }
if ($result -ne 0) { throw "Native desktop exited: $result" }
