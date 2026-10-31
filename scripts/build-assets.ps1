param([string]$Project = 'assets/source/demo', [string]$Output = 'dev/demo.swb', [switch]$UpdateClient)
$ErrorActionPreference = 'Stop'
if ($UpdateClient -and ($Project -ne 'assets/source/demo' -or $Output -ne 'dev/demo.swb')) { throw 'UpdateClient is only for the bundled demo project.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
$pipelineJar = Join-Path $projectRoot 'asset-pipeline/target/swordol-assets.jar'
if (!(Test-Path -LiteralPath $pipelineJar)) { throw 'Build the CLI first: scripts/mvn.ps1 -pl asset-pipeline -am package' }
$sourcePath = if ([IO.Path]::IsPathRooted($Project)) { $Project } else { Join-Path $projectRoot $Project }
$outputPath = if ([IO.Path]::IsPathRooted($Output)) { $Output } else { Join-Path $projectRoot $Output }
New-Item -ItemType Directory -Force -Path (Split-Path $outputPath -Parent) | Out-Null
& java -jar $pipelineJar build $sourcePath $outputPath
if ($LASTEXITCODE -ne 0) { throw 'Asset validation/build failed; previous bundle retained.' }
if ($UpdateClient) {
    $resourceDirectory = Join-Path $projectRoot 'demo-bundle/src/main/resources/bundles'
    New-Item -ItemType Directory -Force -Path $resourceDirectory | Out-Null
    & java -jar $pipelineJar build $sourcePath (Join-Path $resourceDirectory 'demo.swb')
    if ($LASTEXITCODE -ne 0) { throw 'Shared bundle update failed; previous bundle retained.' }
}
