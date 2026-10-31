param([string]$Root = (Split-Path $PSScriptRoot -Parent), [int]$SmokeFrames = 0, [string]$Screenshot = '', [switch]$NoCapture)
$ErrorActionPreference = 'Stop'
$Root = [IO.Path]::GetFullPath($Root)
$packaged = Test-Path -LiteralPath (Join-Path $Root 'app/Emulator.jar')
if ($packaged) {
    $emulator = Join-Path $Root 'app/Emulator.jar'
    $game = Join-Path $Root 'app/VoLamHoiTu.jar'
    $hostJar = Join-Path $Root 'app/swordol-classic-desktop.jar'
    $agent = Join-Path $Root 'app/swordol-capture.jar'
    $java = Join-Path $Root 'runtime/bin/javaw.exe'
    $candidateWorkspace = [IO.Path]::GetFullPath((Join-Path $Root '../..'))
    $dataRoot = if (Test-Path -LiteralPath (Join-Path $candidateWorkspace 'tools/analysis/collect_server_data.py')) { $candidateWorkspace } else { $Root }
} else {
    $emulator = Join-Path $Root 'docs/files/Emulator.jar'
    $game = Join-Path $Root 'docs/files/VoLamHoiTu.jar'
    $hostJar = Join-Path $Root 'classic-desktop/target/swordol-classic-desktop.jar'
    $agent = Join-Path $Root 'legacy-capture/target/swordol-capture.jar'
    . (Join-Path $PSScriptRoot 'java-home.ps1')
    $java = Join-Path (Resolve-SwordolJdkHome $env:JAVA_HOME) 'bin/javaw.exe'
    $dataRoot = $Root
}
$requiredFiles = @($java, $emulator, $game, $hostJar)
if (!$NoCapture) { $requiredFiles += $agent }
foreach ($required in $requiredFiles) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing $required. Build scripts/package-libgdx-desktop.ps1 first." }
}
$session = 'classic-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$capture = Join-Path $dataRoot "captures/$session"
$profile = Join-Path $dataRoot 'data/libgdx-desktop'
New-Item -ItemType Directory -Force -Path $capture,(Join-Path $profile '.microemulator') | Out-Null
[ordered]@{
    schemaVersion = 1
    startedAt = [DateTimeOffset]::Now.ToString('o')
    desktopHost = 'libGDX - original compatibility core'
    clientSha256 = (Get-FileHash -LiteralPath $game -Algorithm SHA256).Hash.ToLowerInvariant()
    emulatorSha256 = (Get-FileHash -LiteralPath $emulator -Algorithm SHA256).Hash.ToLowerInvariant()
    hostSha256 = (Get-FileHash -LiteralPath $hostJar -Algorithm SHA256).Hash.ToLowerInvariant()
    captureEnabled = !$NoCapture
    direction = 'server-to-client'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $capture 'session.json') -Encoding UTF8
$javaArgs = @('--enable-native-access=ALL-UNNAMED', "`"-Duser.home=$profile`"", "`"-Dswordol.clientJar=$game`"")
if (!$NoCapture) { $javaArgs += "`"-javaagent:${agent}=$capture`"" }
if ($SmokeFrames -gt 0) { $javaArgs += "-Dswordol.smokeFrames=$SmokeFrames" }
if ($Screenshot) { $javaArgs += "`"-Dswordol.screenshot=$([IO.Path]::GetFullPath($Screenshot))`"" }
$javaArgs += @('-cp', "`"$hostJar;$emulator`"", 'vn.swordol.classic.ClassicLauncher')
$launchOptions = @{
    FilePath = $java
    ArgumentList = $javaArgs
    WorkingDirectory = $dataRoot
    PassThru = $true
    RedirectStandardOutput = (Join-Path $capture 'desktop.log')
    RedirectStandardError = (Join-Path $capture 'desktop.err')
}
if ($SmokeFrames -gt 0) { $launchOptions.Wait = $true }
$process = Start-Process @launchOptions
Write-Host "SwordOL libGDX desktop opened. Process: $($process.Id)"
Write-Host "Capture: $capture"
if ($SmokeFrames -gt 0) {
    if ($process.ExitCode -ne 0) { throw "Desktop smoke failed. See $capture/desktop.err" }
    Write-Host 'Desktop smoke exit 0.'
}
