# InvoiceGenie dependency security scan (Windows PowerShell)
# Usage:
#   ./scripts/security-scan.ps1
#   ./scripts/security-scan.ps1 -ReportOnly
# Loads NVD_API_KEY from environment or repo-root .env (never prints the key).

param(
    [switch]$SkipNpm,
    [switch]$ReportOnly
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Load .env without printing values
$envFile = Join-Path $root ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $name = $matches[1]
            $val = $matches[2].Trim().Trim('"').Trim("'")
            if (-not [string]::IsNullOrEmpty($val) -and -not [Environment]::GetEnvironmentVariable($name)) {
                [Environment]::SetEnvironmentVariable($name, $val, "Process")
            }
        }
    }
}

$nvd = $env:NVD_API_KEY
if ($nvd) { Write-Host "NVD_API_KEY loaded (length=$($nvd.Length))" -ForegroundColor DarkGray }
else { Write-Host "NVD_API_KEY not set — scan uses OSS Index / cached NVD only" -ForegroundColor Yellow }

$failCvss = if ($ReportOnly) { "11" } else { "7" }

Write-Host "==> Maven OWASP Dependency-Check (profile: security-scan)" -ForegroundColor Cyan
$mvnArgs = @(
    "-Psecurity-scan",
    "-Ddependency.check.failBuildOnCVSS=$failCvss",
    "org.owasp:dependency-check-maven:aggregate"
)
if ($nvd) { $mvnArgs = @("-DnvdApiKey=$nvd") + $mvnArgs }

mvn @mvnArgs
$mvnExit = $LASTEXITCODE

$reportHtml = Join-Path $root "target\dependency-check\dependency-check-report.html"
$reportJson = Join-Path $root "target\dependency-check\dependency-check-report.json"
# Also copy under docs/security for convenience (gitignored patterns: keep local)
$docsSec = Join-Path $root "docs\security"
New-Item -ItemType Directory -Force -Path $docsSec | Out-Null
if (Test-Path $reportHtml) {
    Copy-Item $reportHtml (Join-Path $docsSec "dependency-check-report.html") -Force
    Write-Host "Maven report: $reportHtml" -ForegroundColor Green
}
if (Test-Path $reportJson) {
    Copy-Item $reportJson (Join-Path $docsSec "dependency-check-report.json") -Force
    Write-Host "Maven report: $reportJson" -ForegroundColor Green
}

$npmExit = 0
if (-not $SkipNpm) {
    Write-Host "==> npm audit (web/)" -ForegroundColor Cyan
    Push-Location (Join-Path $root "web")
    try {
        npm audit --omit=dev
        $npmExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

if ($mvnExit -ne 0) { exit $mvnExit }
if ($npmExit -ne 0 -and -not $ReportOnly) { exit $npmExit }
Write-Host "Security scan complete." -ForegroundColor Green
exit 0
