# InvoiceGenie - run unit tests with JaCoCo coverage reports
# Usage: ./scripts/coverage.ps1
# Requires JAVA_HOME (JDK 17+) and Maven on PATH.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$env:MAVEN_SKIP_RC = "1"
if (-not $env:JAVA_HOME) {
    Write-Warning "JAVA_HOME not set; Maven may pick the wrong JDK"
}

Write-Host "==> Running tests with JaCoCo (prepare-agent + report)" -ForegroundColor Cyan
& mvn clean test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "==> Per-module line coverage:" -ForegroundColor Cyan
$min = 80.0
$failed = $false
Get-ChildItem -Recurse -Filter jacoco.xml -Path . -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "target\\site\\jacoco\\jacoco.xml$" } |
    ForEach-Object {
        [xml]$x = Get-Content $_.FullName
        $line = $x.report.counter | Where-Object { $_.type -eq "LINE" }
        if (-not $line) { return }
        $missed = [int]$line.missed
        $covered = [int]$line.covered
        $total = $missed + $covered
        $pct = if ($total -gt 0) { [math]::Round(100.0 * $covered / $total, 1) } else { 0 }
        $mod = $_.Directory.Parent.Parent.Name
        $color = if ($pct -ge $min) { "Green" } else { "Red"; $script:failed = $true }
        Write-Host ("    {0,-28} {1,5}%  ({2}/{3} lines)" -f $mod, $pct, $covered, $total) -ForegroundColor $color
    }

Write-Host ""
Write-Host "==> Coverage HTML reports:" -ForegroundColor Green
Get-ChildItem -Recurse -Filter "index.html" -Path . -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "target\\site\\jacoco" } |
    ForEach-Object { Write-Host "    $($_.FullName)" }

Write-Host ""
if ($failed) {
    Write-Host "FAIL: one or more modules below ${min}% line coverage" -ForegroundColor Red
    exit 1
}
Write-Host "PASS: all modules meet ${min}% line coverage floor" -ForegroundColor Green