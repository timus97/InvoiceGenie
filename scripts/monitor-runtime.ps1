$ErrorActionPreference = "Continue"
$apiLog = "C:\Users\Timus97\.grok\sessions\C%3A%5CUsers%5CTimus97%5CDesktop%5CgrokAnalysis%5CInvoiceGenie\019f942a-d946-7f40-9d7f-001aaada8683\terminal\call-60d69e79-6d20-4a56-a0a9-dad63c5aaef9-150.log"
$webLog = "C:\Users\Timus97\.grok\sessions\C%3A%5CUsers%5CTimus97%5CDesktop%5CgrokAnalysis%5CInvoiceGenie\019f942a-d946-7f40-9d7f-001aaada8683\terminal\call-60d69e79-6d20-4a56-a0a9-dad63c5aaef9-151.log"
$lastApi = 0
$lastWeb = 0
$deadline = (Get-Date).AddMinutes(20)
Write-Output "[monitor] started watching API+WEB until $deadline"
while ((Get-Date) -lt $deadline) {
  foreach ($name in @("API","WEB")) {
    $path = if ($name -eq "API") { $apiLog } else { $webLog }
    if (-not (Test-Path $path)) { continue }
    $lines = Get-Content -LiteralPath $path -ErrorAction SilentlyContinue
    if (-not $lines) { continue }
    $start = if ($name -eq "API") { $lastApi } else { $lastWeb }
    if ($lines.Count -le $start) { continue }
    $chunk = $lines[$start..($lines.Count - 1)]
    if ($name -eq "API") { $lastApi = $lines.Count } else { $lastWeb = $lines.Count }
    foreach ($line in $chunk) {
      if ($line -match 'level.:.ERROR|level.:.FATAL| ERROR | FATAL |Exception in thread|Failed to start|ECONNREFUSED|Unhandled|Build failure') {
        $snippet = if ($line.Length -gt 350) { $line.Substring(0,350) } else { $line }
        Write-Output "[ALERT-$name] $snippet"
      }
    }
  }
  try {
    $ah = (Invoke-WebRequest -Uri "http://localhost:8082/q/health" -UseBasicParsing -TimeoutSec 5).StatusCode
    $wh = (Invoke-WebRequest -Uri "http://localhost:3000/" -UseBasicParsing -TimeoutSec 5).StatusCode
    if ($ah -ne 200 -or $wh -ne 200) {
      Write-Output "[ALERT-HEALTH] api=$ah web=$wh"
    } else {
      Write-Output "[ok] health pulse api=$ah web=$wh $(Get-Date -Format o)"
    }
  } catch {
    Write-Output "[ALERT-HEALTH] $($_.Exception.Message)"
  }
  Start-Sleep -Seconds 20
}
Write-Output "[monitor] completed window"
