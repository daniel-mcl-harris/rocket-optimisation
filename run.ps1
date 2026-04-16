#!/usr/bin/env pwsh
# Wrapper script to run the app with filtered warnings

Write-Host "Building project..." -ForegroundColor Green
mvn clean compile dependency:copy-dependencies -q 2>$null

Write-Host "Running application..." -ForegroundColor Green
Write-Host ""

# Run Java and filter out the warnings we want to suppress
java --add-opens java.base/java.lang=ALL-UNNAMED `
     -cp "target/classes;target/dependency/*" `
     com.danielh.App 2>&1 | Where-Object {
         $_ -notmatch "WARNING: package sun.misc not in java.base" -and `
         $_ -notmatch "WARNING: A terminally deprecated" -and `
         $_ -notmatch "WARNING: Please consider reporting" -and `
         $_ -notmatch "WARNING: sun.misc.Unsafe" -and `
         $_ -notmatch "WARNING: Final field" -and `
         $_ -notmatch "WARNING: Use --enable-final-field-mutation" -and `
         $_ -notmatch "WARNING: Mutating final fields"
     }
