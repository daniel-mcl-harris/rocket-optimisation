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

$warningFilter = @(
    "WARNING: package sun.misc not in java.base",
    "WARNING: A terminally deprecated",
    "WARNING: Please consider reporting",
    "WARNING: sun.misc.Unsafe",
    "WARNING: Final field",
    "WARNING: Use --enable-final-field-mutation",
    "WARNING: Mutating final fields"
)

$javaArgs = @(
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "-cp", "target/classes;target/dependency/*",
    "com.danielh.App"
) + $args

$javaOutput = & java @javaArgs 2>&1
$javaExit = $LASTEXITCODE
foreach ($line in $javaOutput) {
    $text = "$line"
    $skip = $false
    foreach ($pattern in $warningFilter) {
        if ($text.Contains($pattern)) {
            $skip = $true
            break
        }
    }
    if (-not $skip) {
        Write-Output $text
    }
}
exit $javaExit
