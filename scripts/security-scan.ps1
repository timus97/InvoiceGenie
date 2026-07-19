# InvoiceGenie - OWASP Dependency-Check (High+ CVSS fail)
# Usage: ./scripts/security-scan.ps1
# Optional: set NVD_API_KEY for faster NVD downloads.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$env:MAVEN_SKIP_RC = "1"

Write-Host "==> OWASP Dependency-Check (profile security-scan, fail on CVSS >= 7)" -ForegroundColor Cyan
$args = @("-Psecurity-scan", "dependency-check:check", "-DskipTests")
if ($env:NVD_API_KEY) {
    $args += "-DnvdApiKey=$($env:NVD_API_KEY)"
}
& mvn @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$report = Join-Path $Root "target\dependency-check-report.html"
if (Test-Path $report) {
    Write-Host "Report: $report" -ForegroundColor Green
}
Write-Host "Done."