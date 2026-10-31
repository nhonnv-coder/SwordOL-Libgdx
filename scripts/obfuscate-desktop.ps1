param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$maven = Join-Path $projectRoot '.tools/apache-maven-3.9.11/bin/mvn.cmd'
$inputJar = Join-Path $projectRoot 'lwjgl3/target/swordol-desktop.jar'
if (!(Test-Path -LiteralPath $inputJar)) { throw 'Build first: scripts/mvn.ps1 verify' }
. (Join-Path $PSScriptRoot 'java-home.ps1')
$JdkHome = Resolve-SwordolJdkHome $JdkHome
$toolDirectory = Join-Path $projectRoot '.tools/proguard/7.9.1'
& $maven "-Dmaven.repo.local=$projectRoot/.tools/m2" -f "$projectRoot/deploy/proguard/pom.xml" org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies "-DoutputDirectory=$toolDirectory" -q
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the pinned obfuscator.' }
$outputDirectory = Join-Path $projectRoot 'dist/protected'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$configPath = Join-Path $outputDirectory 'desktop.pro'
$configuration = @(
    "-injars '$inputJar'(!META-INF/versions/**,!module-info.class)",
    "-outjars '$outputDirectory/swordol-desktop-protected.jar'",
    "-printmapping '$outputDirectory/mapping.txt'",
    "-include '$projectRoot/deploy/proguard/desktop.pro'"
)
Get-ChildItem -LiteralPath (Join-Path $JdkHome 'jmods') -Filter '*.jmod' | ForEach-Object {
    $configuration += "-libraryjars '$($_.FullName)'(!**.jar;!module-info.class)"
}
$configuration | Set-Content -LiteralPath $configPath -Encoding ascii
& (Join-Path $JdkHome 'bin/java.exe') -cp "$toolDirectory/*" proguard.ProGuard "@$configPath"
if ($LASTEXITCODE -ne 0) { throw 'Obfuscation failed.' }
Write-Host "Protected desktop jar: $outputDirectory/swordol-desktop-protected.jar"
Write-Host 'Keep mapping.txt private. Smoke-test this artifact before distribution.'
