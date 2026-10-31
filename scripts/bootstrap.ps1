$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolsRoot = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
$version = '3.9.11'
$mavenZip = Join-Path $toolsRoot 'maven.zip'
$origin = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
if (!(Test-Path -LiteralPath (Join-Path $toolsRoot "apache-maven-$version/bin/mvn.cmd"))) {
    Invoke-WebRequest -Uri $origin -OutFile $mavenZip
    $expected = (Invoke-WebRequest -Uri "$origin.sha512").Content.Trim().Split(' ')[0]
    if ((Get-FileHash -LiteralPath $mavenZip -Algorithm SHA512).Hash -ine $expected) { throw 'Maven archive checksum mismatch.' }
    Expand-Archive -LiteralPath $mavenZip -DestinationPath $toolsRoot
}
Write-Host 'Local Maven ready. Use scripts/mvn.ps1 verify.'
