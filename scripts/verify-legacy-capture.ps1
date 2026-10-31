param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'java-home.ps1')
$JdkHome = Resolve-SwordolJdkHome $JdkHome
$agent = Join-Path $projectRoot 'legacy-capture/target/swordol-capture.jar'
if (!(Test-Path -LiteralPath $agent)) { throw 'Build legacy-capture package first.' }
$work = Join-Path $projectRoot ('.tools/capture-check-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $work 'classes'
$records = Join-Path $work 'records'
New-Item -ItemType Directory -Force -Path $classes,$records | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 8 -d $classes (Join-Path $projectRoot 'tools/verification/CaptureProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed.' }
$classPath = "$classes;$agent;$(Join-Path $projectRoot 'docs/files/Emulator.jar')"
& (Join-Path $JdkHome 'bin/java.exe') "-javaagent:${agent}=$records" -cp $classPath CaptureProbe $records
if ($LASTEXITCODE -ne 0) { throw 'Capture probe failed.' }
Write-Host "Verified local HTTP/TCP recordings: $records"
