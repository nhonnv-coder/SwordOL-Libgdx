$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$mavenPath = Join-Path $projectRoot '.tools/apache-maven-3.9.11/bin/mvn.cmd'
if (!(Test-Path -LiteralPath $mavenPath)) { throw 'Run scripts/bootstrap.ps1 first to install the local build tools.' }
& $mavenPath "-Dmaven.repo.local=$projectRoot/.tools/m2" '-f' "$projectRoot/pom.xml" @args
exit $LASTEXITCODE
