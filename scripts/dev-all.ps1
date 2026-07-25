# Start Docker Postgres + Quarkus (dev/PostgreSQL) + Next.js GUI.
# Usage: ./scripts/dev-all.ps1
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not $Root) { $Root = (Get-Location).Path }
Set-Location $Root

Write-Host "==> InvoiceGenie dual-run (Postgres + API :8082 + UI :3000)" -ForegroundColor Cyan

# Ensure Postgres + Adminer
docker compose up -d postgres adminer | Out-Host

# Load DB credentials from .env for host-side Quarkus
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
  }
}
$env:QUARKUS_DATASOURCE_JDBC_URL = "jdbc:postgresql://localhost:5432/invoicegenie"
if (-not $env:QUARKUS_DATASOURCE_USERNAME) { $env:QUARKUS_DATASOURCE_USERNAME = "ar" }
if (-not $env:QUARKUS_DATASOURCE_PASSWORD) { $env:QUARKUS_DATASOURCE_PASSWORD = "ar" }

$env:MAVEN_SKIP_RC = "1"
if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Java\jdk-17.0.5")) {
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.5"
}
$mvn = if (Test-Path "C:\Program Files (x86)\apache-maven-3.9.16\bin\mvn.cmd") {
  "C:\Program Files (x86)\apache-maven-3.9.16\bin\mvn.cmd"
} else { "mvn" }

$api = Start-Process -PassThru -NoNewWindow -WorkingDirectory $Root -FilePath $mvn -ArgumentList @(
  "-pl", "ar-bootstrap",
  "-Dquarkus.profile=dev",
  "-Dquarkus.http.port=8082",
  "-Dquarkus.kafka.devservices.enabled=false",
  "quarkus:dev"
)

Start-Sleep -Seconds 3

Push-Location (Join-Path $Root "web")
try {
  if (-not (Test-Path "node_modules")) { npm install }
  if (-not (Test-Path ".env.local") -and (Test-Path ".env.example")) {
    Copy-Item ".env.example" ".env.local"
  }
  npm run dev
} finally {
  Pop-Location
  if ($api -and -not $api.HasExited) {
    Write-Host "Stopping API process tree..." -ForegroundColor Yellow
    Stop-Process -Id $api.Id -Force -ErrorAction SilentlyContinue
  }
}