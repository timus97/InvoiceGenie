# InvoiceGenie — start local dev server (H2 in-memory, no Postgres)
# Usage: ./scripts/dev-up.ps1 [-Port 8082] [-SkipBuild]
param(
    [int]$Port = 8080,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "==> InvoiceGenie local dev (profile=dev, H2 in-memory)" -ForegroundColor Cyan
Write-Host "    Port: $Port" -ForegroundColor Cyan
Write-Host "    Repo: $Root" -ForegroundColor Cyan

# Soft checks — Maven uses JAVA_HOME when set
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Warning "JAVA_HOME is not set to a valid JDK. Maven may fail. Set JAVA_HOME to JDK 17+."
} else {
    Write-Host "    JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Cyan
}
try {
    & mvn -version | Select-Object -First 3
} catch {
    Write-Error "mvn not found on PATH. Install Maven 3.9+ (3.8.5+ works)."
}

if (-not $SkipBuild) {
    Write-Host "==> mvn clean install -DskipTests" -ForegroundColor Yellow
    & mvn clean install "-DskipTests"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "==> mvn -pl ar-bootstrap -am compile -DskipTests" -ForegroundColor Yellow
    & mvn -pl ar-bootstrap -am compile "-DskipTests"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "==> Starting Quarkus (Ctrl+C to stop)" -ForegroundColor Green
Write-Host "    Health:  http://localhost:$Port/q/health"
Write-Host "    Swagger: http://localhost:$Port/q/swagger-ui/"
Write-Host "    API:     http://localhost:$Port/api/v1/invoices"
Write-Host ""

& mvn -pl ar-bootstrap `
    "-Dquarkus.profile=dev" `
    "-Dquarkus.http.port=$Port" `
    "-Dquarkus.kafka.devservices.enabled=false" `
    quarkus:dev

exit $LASTEXITCODE
