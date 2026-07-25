# InvoiceGenie — start local dev server against Docker PostgreSQL
# Prerequisites: docker compose up -d postgres adminer
# Usage: ./scripts/dev-up.ps1 [-Port 8082] [-SkipBuild]
param(
    [int]$Port = 8080,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "==> InvoiceGenie local dev (profile=dev, PostgreSQL)" -ForegroundColor Cyan
Write-Host "    Port: $Port" -ForegroundColor Cyan
Write-Host "    Repo: $Root" -ForegroundColor Cyan

# Load local DB password from .env when present (matches docker-compose postgres)
if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $k, $v = $_.Split('=', 2)
        $k = $k.Trim(); $v = $v.Trim().Trim('"').Trim("'")
        if ($k -eq "POSTGRES_PASSWORD" -or $k -eq "QUARKUS_DATASOURCE_PASSWORD") {
            $env:QUARKUS_DATASOURCE_PASSWORD = $v
        }
        if ($k -eq "POSTGRES_USER" -or $k -eq "QUARKUS_DATASOURCE_USERNAME") {
            $env:QUARKUS_DATASOURCE_USERNAME = $v
        }
        if ($k -eq "POSTGRES_DB") {
            # used only for messaging
        }
    }
}

if (-not $env:QUARKUS_DATASOURCE_JDBC_URL) {
    $env:QUARKUS_DATASOURCE_JDBC_URL = "jdbc:postgresql://localhost:5432/invoicegenie"
}
if (-not $env:QUARKUS_DATASOURCE_USERNAME) { $env:QUARKUS_DATASOURCE_USERNAME = "ar" }
if (-not $env:QUARKUS_DATASOURCE_PASSWORD) { $env:QUARKUS_DATASOURCE_PASSWORD = "ar" }

Write-Host "    JDBC: $($env:QUARKUS_DATASOURCE_JDBC_URL)" -ForegroundColor Cyan
Write-Host "    User: $($env:QUARKUS_DATASOURCE_USERNAME)" -ForegroundColor Cyan

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Warning "JAVA_HOME is not set to a valid JDK. Maven may fail. Set JAVA_HOME to JDK 17+."
} else {
    Write-Host "    JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Cyan
}
try {
    & mvn -version | Select-Object -First 3
} catch {
    Write-Error "mvn not found on PATH. Install Maven 3.9+."
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
Write-Host "    Adminer: http://localhost:8081  (System=PostgreSQL, Server=postgres or localhost)"
Write-Host ""

& mvn -pl ar-bootstrap `
    "-Dquarkus.profile=dev" `
    "-Dquarkus.http.port=$Port" `
    "-Dquarkus.kafka.devservices.enabled=false" `
    quarkus:dev

exit $LASTEXITCODE