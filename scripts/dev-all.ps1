# Start Quarkus (dev/H2) + Next.js GUI.
# Usage: ./scripts/dev-all.ps1
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not $Root) { $Root = (Get-Location).Path }

Write-Host "==> InvoiceGenie dual-run (API :8080 + UI :3000)" -ForegroundColor Cyan

$env:MAVEN_SKIP_RC = "1"
# Prefer JDK 17 if present (adjust if needed)
if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Java\jdk-17.0.5")) {
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.5"
}

$api = Start-Process -PassThru -NoNewWindow -WorkingDirectory $Root -FilePath "mvn" -ArgumentList @(
  "-pl", "ar-bootstrap",
  "-Dquarkus.profile=dev",
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