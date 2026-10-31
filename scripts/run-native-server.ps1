param(
    # A map folder holding map-data.bin and map-ref.bin, as tools/analysis/extract_maps.py exports.
    [string]$Map = '',
    [string]$Spawn = '20,30'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$Map) { $Map = Join-Path $root 'client-core/src/test/resources/maps' }
if (!(Test-Path -LiteralPath (Join-Path $Map 'map-data.bin'))) {
    Write-Host "No map document in $Map; the server will only announce a map."
    $Map = ''
}
. (Join-Path $PSScriptRoot 'java-home.ps1')
$javaPath = Join-Path (Resolve-SwordolJdkHome $env:JAVA_HOME) 'bin/java.exe'
$jarPath = Join-Path $root 'server/target/swordol-server.jar'
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Build first: scripts/mvn.ps1 -o verify' }
$localName = Read-Host 'Choose a LOCAL test account name (not your old game account)'
$securePassword = Read-Host 'Choose its LOCAL test password' -AsSecureString
$previousName = $env:SWORDOL_LOCAL_ACCOUNT
$previousPassword = $env:SWORDOL_LOCAL_PASSWORD
$previousMap = $env:SWORDOL_LOCAL_MAP
$previousSpawn = $env:SWORDOL_LOCAL_SPAWN
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:SWORDOL_LOCAL_ACCOUNT = $localName
    $env:SWORDOL_LOCAL_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:SWORDOL_LOCAL_MAP = $Map
    $env:SWORDOL_LOCAL_SPAWN = $Spawn
    & $javaPath -cp $jarPath vn.swordol.server.gateway.GatewayMain
    if ($LASTEXITCODE -ne 0) { throw "Local server exited: $LASTEXITCODE" }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $securePassword.Dispose()
    $env:SWORDOL_LOCAL_ACCOUNT = $previousName
    $env:SWORDOL_LOCAL_PASSWORD = $previousPassword
    $env:SWORDOL_LOCAL_MAP = $previousMap
    $env:SWORDOL_LOCAL_SPAWN = $previousSpawn
}
