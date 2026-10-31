param([string]$Root = (Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference = 'Stop'
$Root = [IO.Path]::GetFullPath($Root)
$packaged = Test-Path -LiteralPath (Join-Path $Root 'app/Emulator.jar')
if ($packaged) {
    $emulator = Join-Path $Root 'app/Emulator.jar'
    $game = Join-Path $Root 'app/VoLamHoiTu.jar'
    $agent = Join-Path $Root 'app/swordol-capture.jar'
    $java = Join-Path $Root 'runtime/bin/javaw.exe'
} else {
    $emulator = Join-Path $Root 'docs/files/Emulator.jar'
    $game = Join-Path $Root 'docs/files/VoLamHoiTu.jar'
    $agent = Join-Path $Root 'legacy-capture/target/swordol-capture.jar'
    . (Join-Path $PSScriptRoot 'java-home.ps1')
    $java = Join-Path (Resolve-SwordolJdkHome $env:JAVA_HOME) 'bin/javaw.exe'
}
foreach ($required in @($emulator, $game, $agent, $java)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing: $required. Build scripts/package-legacy-pc.ps1 first." }
}
$session = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$capture = Join-Path $Root "captures/$session"
$profile = Join-Path $Root 'data/emulator'
New-Item -ItemType Directory -Force -Path $capture,(Join-Path $profile '.microemulator') | Out-Null
$manifest = [ordered]@{
    schemaVersion = 1
    startedAt = [DateTimeOffset]::Now.ToString('o')
    clientSha256 = (Get-FileHash -LiteralPath $game -Algorithm SHA256).Hash.ToLowerInvariant()
    emulatorSha256 = (Get-FileHash -LiteralPath $emulator -Algorithm SHA256).Hash.ToLowerInvariant()
    agentSha256 = (Get-FileHash -LiteralPath $agent -Algorithm SHA256).Hash.ToLowerInvariant()
    direction = 'server-to-client'
    note = 'Inbound data can include session tokens. Keep captures private.'
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $capture 'session.json') -Encoding UTF8
# Start-Process needs explicitly quoted native arguments when paths contain spaces.
$javaArgs = @("`"-Duser.home=$profile`"", "`"-javaagent:${agent}=$capture`"", '-cp', "`"$agent;$emulator`"",
    'org.microemu.app.Main', '--resizableDevice', '480', '640', "`"$game`"")
$process = Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory $Root -PassThru `
    -RedirectStandardOutput (Join-Path $capture 'emulator.log') -RedirectStandardError (Join-Path $capture 'emulator.err')
Write-Host "SwordOL PC opened. Capture directory: $capture"
Write-Host 'Use arrows, Enter, F1/F2 and the mouse. Close the game normally to finalize recordings.'
Write-Host "Process: $($process.Id)"
