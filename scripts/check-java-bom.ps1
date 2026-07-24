# Fail if any *.java has a UTF-8 BOM (EF BB BF). STORY-QA-001
param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)
$bad = @()
Get-ChildItem -Path $Root -Recurse -Filter *.java -File |
  Where-Object { $_.FullName -notmatch '\\target\\|\\\.git\\' } |
  ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
      $bad += $_.FullName
      Write-Host "UTF-8 BOM: $($_.FullName)"
    }
  }
if ($bad.Count -gt 0) {
  Write-Error "UTF-8 BOM detected on $($bad.Count) Java file(s) (STORY-QA-001). Re-save as UTF-8 without BOM."
  exit 1
}
Write-Host "OK: no UTF-8 BOM on *.java"