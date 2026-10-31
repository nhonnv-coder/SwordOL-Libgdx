param([string]$BindAddress = '127.0.0.1', [int]$Port = 7777)
$projectRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $projectRoot 'server/target/swordol-server.jar'
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Build first: scripts/mvn.ps1 verify' }
& java -jar $jarPath --host $BindAddress --port $Port
exit $LASTEXITCODE
