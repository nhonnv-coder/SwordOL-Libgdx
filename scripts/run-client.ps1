$projectRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $projectRoot 'lwjgl3/target/swordol-desktop.jar'
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Build first: scripts/mvn.ps1 verify' }
& java --enable-native-access=ALL-UNNAMED -jar $jarPath
exit $LASTEXITCODE
