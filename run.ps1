#!/usr/bin/env pwsh
# Compile, then start Java attached to this console.
# Maven and Java errors stay visible, and the script exits with their code.
# JDK warnings are suppressed with JVM flags. Java's stdout and stderr are not
# piped or redirected, so System.console() is set and the progress line can
# update in place. The chart is a separate window. This script waits until
# Java exits, which is when that window is closed, or straight away when no
# window was opened.

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "Building project..." -ForegroundColor Green
& mvn compile dependency:copy-dependencies -q
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Running application..." -ForegroundColor Green
Write-Host ""

& java `
    --enable-native-access=ALL-UNNAMED `
    --sun-misc-unsafe-memory-access=allow `
    --enable-final-field-mutation=ALL-UNNAMED `
    --add-opens java.base/java.lang=ALL-UNNAMED `
    -cp "target/classes;target/dependency/*" `
    com.danielh.App `
    @args
exit $LASTEXITCODE
