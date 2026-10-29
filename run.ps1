#!/usr/bin/env pwsh
# Wrapper script to run the app with filtered JDK warnings.
# Maven and Java write their own errors, and this script exits with their code.

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "Building project..." -ForegroundColor Green
& mvn compile dependency:copy-dependencies -q
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Running application..." -ForegroundColor Green
Write-Host ""

$script:warningFilter = @(
    "WARNING: package sun.misc not in java.base",
    "WARNING: A terminally deprecated",
    "WARNING: Please consider reporting",
    "WARNING: sun.misc.Unsafe",
    "WARNING: Final field",
    "WARNING: Use --enable-final-field-mutation",
    "WARNING: Mutating final fields"
)

function Show-JavaLine([string]$text) {
    if ([string]::IsNullOrEmpty($text)) {
        return
    }
    foreach ($pattern in $script:warningFilter) {
        if ($text.Contains($pattern)) {
            return
        }
    }
    Write-Host $text
}

$javaArgs = @(
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "-cp", "target/classes;target/dependency/*",
    "com.danielh.App"
) + $args

# Stream stdout and stderr as lines arrive. Assigning `& java 2>&1` to a
# variable holds every line until Java exits, which hides the progress lines
# and would also hide the report for as long as the chart window stays open.
# The Swing chart does not use this stream: it is a separate desktop window.
$quoted = foreach ($arg in $javaArgs) {
    if ($arg -match '[\s"]') {
        '"' + ($arg -replace '"', '\"') + '"'
    } else {
        $arg
    }
}

$start = New-Object System.Diagnostics.ProcessStartInfo
$start.FileName = "java"
$start.Arguments = ($quoted -join ' ')
$start.UseShellExecute = $false
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$start.CreateNoWindow = $true
$start.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$start.StandardErrorEncoding = [System.Text.Encoding]::UTF8

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $start
$process.add_ErrorDataReceived({
    $text = $EventArgs.Data
    if ([string]::IsNullOrEmpty($text)) {
        return
    }
    foreach ($pattern in $script:warningFilter) {
        if ($text.Contains($pattern)) {
            return
        }
    }
    Write-Host $text
})
[void]$process.Start()
$process.BeginErrorReadLine()
while (($line = $process.StandardOutput.ReadLine()) -ne $null) {
    Show-JavaLine $line
}
$process.WaitForExit()
exit $process.ExitCode
