param(
    [string]$Bundle = 'assets/source/OL.swb',
    [string]$Report = 'docs/legacy-runtime-verification.json',
    [string]$JdkHome
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'java-home.ps1')
$jdk = Resolve-SwordolJdkHome $JdkHome
$desktopJar = Join-Path $projectRoot 'lwjgl3/target/swordol-desktop.jar'
if (!(Test-Path -LiteralPath $desktopJar)) { throw 'Build desktop first: scripts/mvn.ps1 -pl lwjgl3 -am package' }
$bundlePath = if ([IO.Path]::IsPathRooted($Bundle)) { $Bundle } else { Join-Path $projectRoot $Bundle }
$reportPath = if ([IO.Path]::IsPathRooted($Report)) { $Report } else { Join-Path $projectRoot $Report }
$classRoot = Join-Path $projectRoot '.tools/legacy-verification-classes'
New-Item -ItemType Directory -Force -Path $classRoot | Out-Null
& (Join-Path $jdk 'bin/javac.exe') --release 8 -encoding UTF-8 -cp $desktopJar -d $classRoot (Join-Path $projectRoot 'tools/verification/BundleProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
& (Join-Path $jdk 'bin/java.exe') --enable-native-access=ALL-UNNAMED -cp "$classRoot;$desktopJar" vn.swordol.assets.runtime.BundleProbe $bundlePath $reportPath
if ($LASTEXITCODE -ne 0) { throw 'Runtime verification failed; report was not updated' }
