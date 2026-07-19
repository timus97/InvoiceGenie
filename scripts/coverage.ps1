# InvoiceGenie - run unit tests with JaCoCo coverage reports
# Usage: ./scripts/coverage.ps1
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$env:MAVEN_SKIP_RC = "1"
if (-not $env:JAVA_HOME) {
    Write-Warning "JAVA_HOME not set; Maven may pick the wrong JDK"
}

Write-Host "==> Running tests with JaCoCo (prepare-agent + report + report-aggregate)" -ForegroundColor Cyan
& mvn clean test verify "-DskipITs"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "==> Coverage reports:" -ForegroundColor Green
Get-ChildItem -Recurse -Filter "index.html" -Path . -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "target\\site\\jacoco" } |
    ForEach-Object { Write-Host "    $($_.FullName)" }

$agg = Join-Path $Root "target\site\jacoco-aggregate\index.html"
if (Test-Path $agg) {
    Write-Host "    Aggregate: $agg" -ForegroundColor Green
}

Write-Host ""
Write-Host "Done."