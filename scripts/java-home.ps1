function Resolve-SwordolJdkHome {
    param([string]$ConfiguredHome)
    if ($ConfiguredHome) { return $ConfiguredHome }
    # Windows PowerShell treats native stderr as errors even for exit code zero.
    # Java writes these informational settings to stderr by design.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $settings = & java -XshowSettings:properties -version 2>&1
        $javaExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($javaExitCode -ne 0) { throw 'Cannot inspect Java. Set JAVA_HOME to a full JDK.' }
    $line = $settings | ForEach-Object { "$PSItem" } | Where-Object { $_ -match '^\s*java\.home\s*=' } | Select-Object -First 1
    if (!$line) { throw 'Set JAVA_HOME to a full JDK.' }
    return ($line -split '=',2)[1].Trim()
}
