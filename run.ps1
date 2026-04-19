#!/usr/bin/env pwsh
# Wrapper script to run the app with filtered warnings

# Set UTF-8 encoding for proper character display
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "Building project..." -ForegroundColor Green
# Skip clean step to avoid file locking issues, just compile and copy dependencies
mvn compile dependency:copy-dependencies -q 2>$null

Write-Host "Running application..." -ForegroundColor Green
Write-Host ""

# Run Java with warnings filtered
$warningFilter = @(
    "WARNING: package sun.misc not in java.base",
    "WARNING: A terminally deprecated",
    "WARNING: Please consider reporting",
    "WARNING: sun.misc.Unsafe",
    "WARNING: Final field",
    "WARNING: Use --enable-final-field-mutation",
    "WARNING: Mutating final fields"
)

java --add-opens java.base/java.lang=ALL-UNNAMED `
     -cp "target/classes;target/dependency/*" `
     com.danielh.App $args 2>&1 | ForEach-Object {
    $line = $_
    $skip = $false
    foreach ($pattern in $warningFilter) {
        if ($line -match [regex]::Escape($pattern)) {
            $skip = $true
            break
        }
    }
    if (-not $skip) {
        Write-Output $line
    }
}
