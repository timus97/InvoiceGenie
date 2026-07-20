# InvoiceGenie API Test Script (Windows PowerShell)
# Dev (H2, security off):  mvn -pl ar-bootstrap quarkus:dev -Dquarkus.profile=dev
# Prod compose:            ./scripts/test-api.ps1 -ApiKey dev-local-key
# Usage: ./scripts/test-api.ps1 [-BaseUrl http://localhost:8080] [-TenantId ...] [-ApiKey ...]

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TenantId = $(if ($env:TENANT_ID) { $env:TENANT_ID } else { "00000000-0000-0000-0000-000000000001" }),
    [string]$ApiKey = $(if ($env:INVOICEGENIE_API_KEY) { $env:INVOICEGENIE_API_KEY } elseif ($env:NEXT_PUBLIC_API_KEY) { $env:NEXT_PUBLIC_API_KEY } else { "" })
)

$ErrorActionPreference = "Continue"
$Pass = 0
$Fail = 0

Write-Host "=========================================="
Write-Host "InvoiceGenie API Test Suite (PowerShell)"
Write-Host "Base URL: $BaseUrl"
Write-Host "Tenant:   $TenantId"
Write-Host "API Key:  $(if ($ApiKey) { '(set)' } else { '(none — ok for dev profile)' })"
Write-Host "=========================================="

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [string]$Body = $null,
        [int]$Expected = 200,
        [string]$IdempotencyKey = $null
    )
    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantId
    }
    if ($ApiKey) {
        $headers["X-API-Key"] = $ApiKey
    }
    if ($IdempotencyKey) {
        $headers["Idempotency-Key"] = $IdempotencyKey
    }
    $uri = "$BaseUrl$Path"
    Write-Host -NoNewline "[$Name] $Method $Path ... "
    try {
        if ($Body) {
            $resp = Invoke-WebRequest -Uri $uri -Method $Method -Headers $headers -Body $Body -UseBasicParsing
        } else {
            $resp = Invoke-WebRequest -Uri $uri -Method $Method -Headers $headers -UseBasicParsing
        }
        $code = [int]$resp.StatusCode
        $content = $resp.Content
    } catch {
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $content = $reader.ReadToEnd()
            } catch { $content = "" }
        } else {
            $code = 0
            $content = $_.Exception.Message
        }
    }
    if ($code -eq $Expected) {
        Write-Host "PASS ($code)" -ForegroundColor Green
        $script:Pass++
        return $content
    } else {
        Write-Host "FAIL (expected $Expected, got $code)" -ForegroundColor Red
        if ($content) { Write-Host $content }
        $script:Fail++
        return $content
    }
}

function Get-JsonId([string]$json) {
    if (-not $json) { return $null }
    try {
        $obj = $json | ConvertFrom-Json
        if ($obj.id) { return [string]$obj.id }
    } catch {}
    return $null
}

# Wait for health
Write-Host -NoNewline "Waiting for server..."
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        $h = Invoke-WebRequest -Uri "$BaseUrl/q/health" -UseBasicParsing -TimeoutSec 2
        if ($h.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 2
}
if ($ready) { Write-Host " ready" } else { Write-Host " TIMEOUT"; exit 1 }

$ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

# --- Customers ---
Write-Host ""
Write-Host "=== SECTION 1: CUSTOMER ===" -ForegroundColor Cyan
$custBody = @{ customerCode = "CUST-$ts"; legalName = "Test Customer Inc."; currency = "USD" } | ConvertTo-Json
$custJson = Invoke-Api -Name "Create Customer" -Method POST -Path "/api/v1/customers" -Body $custBody -Expected 201
$CustomerId = Get-JsonId $custJson
Write-Host "  Customer ID: $CustomerId"
if ($CustomerId) {
    Invoke-Api -Name "Get Customer" -Method GET -Path "/api/v1/customers/$CustomerId" -Expected 200 | Out-Null
}
Invoke-Api -Name "List Customers" -Method GET -Path "/api/v1/customers" -Expected 200 | Out-Null

# --- Invoices ---
Write-Host ""
Write-Host "=== SECTION 2: INVOICE ===" -ForegroundColor Cyan
$InvoiceId = $null
if ($CustomerId) {
    $invBody = @{
        invoiceNumber = "INV-TEST-$ts"
        customerId    = $CustomerId
        customerRef   = "API-Test"
        currencyCode  = "USD"
        dueDate       = "2026-12-31"
        lines         = @(
            @{ description = "Consulting"; amount = 5000.00 }
            @{ description = "Expenses"; amount = 500.00 }
        )
    } | ConvertTo-Json -Depth 5
    $invJson = Invoke-Api -Name "Create Invoice" -Method POST -Path "/api/v1/invoices" -Body $invBody -Expected 201
    $InvoiceId = Get-JsonId $invJson
    Write-Host "  Invoice ID: $InvoiceId"
    if ($InvoiceId) {
        Invoke-Api -Name "Get Invoice" -Method GET -Path "/api/v1/invoices/$InvoiceId" -Expected 200 | Out-Null
    }
}
Invoke-Api -Name "List Invoices" -Method GET -Path "/api/v1/invoices?limit=10" -Expected 200 | Out-Null

# --- Payments ---
Write-Host ""
Write-Host "=== SECTION 3: PAYMENTS ===" -ForegroundColor Cyan
$PaymentId = $null
if ($CustomerId) {
    $payBody = @{
        paymentNumber = "PAY-$ts"
        customerId    = $CustomerId
        amount        = 1000.00
        currencyCode  = "USD"
        paymentDate   = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
        method        = "BANK_TRANSFER"
        reference     = "REF-$ts"
        notes         = "API test payment"
    } | ConvertTo-Json
    $payJson = Invoke-Api -Name "Record Payment" -Method POST -Path "/api/v1/payments" -Body $payBody -Expected 201
    $PaymentId = Get-JsonId $payJson
    Write-Host "  Payment ID: $PaymentId"
    if ($PaymentId -and $InvoiceId) {
        $allocBody = @{
            allocatedBy = $TenantId
            allocations = @(@{ invoiceId = $InvoiceId; amount = 500.00; notes = "partial" })
        } | ConvertTo-Json -Depth 5
        Invoke-Api -Name "Manual Allocate" -Method POST -Path "/api/v1/payments/$PaymentId/allocate/manual" -Body $allocBody -Expected 200 | Out-Null
        Invoke-Api -Name "Get Allocations" -Method GET -Path "/api/v1/payments/$PaymentId/allocations" -Expected 200 | Out-Null
    }
}

# --- Aging ---
Write-Host ""
Write-Host "=== SECTION 4: AGING ===" -ForegroundColor Cyan
Invoke-Api -Name "Aging Report" -Method GET -Path "/api/v1/aging" -Expected 200 | Out-Null

# --- Cheques ---
Write-Host ""
Write-Host "=== SECTION 5: CHEQUES ===" -ForegroundColor Cyan
if ($CustomerId) {
    $chqBody = @{
        chequeNumber = "CHQ-$ts"
        customerId   = $CustomerId
        amount       = 1000.00
        currencyCode = "USD"
        bankName     = "Test Bank"
        bankBranch   = "Main"
        chequeDate   = "2026-03-20"
        notes        = "test"
    } | ConvertTo-Json
    $chqJson = Invoke-Api -Name "Create Cheque" -Method POST -Path "/api/v1/cheques" -Body $chqBody -Expected 201
    $ChequeId = Get-JsonId $chqJson
    if ($ChequeId) {
        Invoke-Api -Name "List Cheques (unfiltered)" -Method GET -Path "/api/v1/cheques" -Expected 200 | Out-Null
        Invoke-Api -Name "Deposit Cheque" -Method POST -Path "/api/v1/cheques/$ChequeId/deposit" -Expected 200 | Out-Null
        Invoke-Api -Name "Clear Cheque" -Method POST -Path "/api/v1/cheques/$ChequeId/clear" -Expected 200 | Out-Null
    }
}

# --- Credit notes ---
Write-Host ""
Write-Host "=== SECTION 6: CREDIT NOTES ===" -ForegroundColor Cyan
if ($CustomerId -and $InvoiceId) {
    $cnBody = @{
        customerId         = $CustomerId
        discountAmount     = 100.00
        currencyCode       = "USD"
        referenceInvoiceId = $InvoiceId
    } | ConvertTo-Json
    Invoke-Api -Name "Create Credit Note" -Method POST -Path "/api/v1/credit-notes" -Body $cnBody -Expected 201 | Out-Null
}
Invoke-Api -Name "List Credit Notes" -Method GET -Path "/api/v1/credit-notes" -Expected 200 | Out-Null

# --- Outbox ---
Write-Host ""
Write-Host "=== SECTION 7: OUTBOX ===" -ForegroundColor Cyan
Invoke-Api -Name "Outbox Stats" -Method GET -Path "/api/v1/outbox/stats" -Expected 200 | Out-Null

# --- Ledger validate + DRAFT + payment idempotency (P1-02/04/05) ---
Write-Host ""
Write-Host "=== SECTION 8: LEDGER / DRAFT / IDEMPOTENCY ===" -ForegroundColor Cyan
if ($InvoiceId) {
    $valBody = @{ referenceType = "INVOICE"; referenceId = $InvoiceId } | ConvertTo-Json
    Invoke-Api -Name "Ledger Validate (invoice)" -Method POST -Path "/api/v1/ledger/validate" -Body $valBody -Expected 200 | Out-Null
}
if ($CustomerId) {
    $draftBody = @{
        invoiceNumber     = "INV-DRAFT-$ts"
        customerId        = $CustomerId
        customerRef       = "Draft-Test"
        currencyCode      = "USD"
        dueDate           = "2026-12-31"
        issueImmediately  = $false
        lines             = @(@{ description = "Draft line"; amount = 250.00 })
    } | ConvertTo-Json -Depth 5
    $draftJson = Invoke-Api -Name "Create DRAFT Invoice" -Method POST -Path "/api/v1/invoices" -Body $draftBody -Expected 201
    $DraftId = Get-JsonId $draftJson
    if ($DraftId) {
        $getDraft = Invoke-Api -Name "Get DRAFT Invoice" -Method GET -Path "/api/v1/invoices/$DraftId" -Expected 200
        if ($getDraft -and $getDraft -notmatch '"status"\s*:\s*"DRAFT"') {
            Write-Host "FAIL: draft invoice status is not DRAFT" -ForegroundColor Red
            $script:Fail++
        } else {
            Write-Host "[Assert DRAFT status] PASS" -ForegroundColor Green
            $script:Pass++
        }
        $draftVal = @{ referenceType = "INVOICE"; referenceId = $DraftId } | ConvertTo-Json
        # No ledger rows for pure draft → 404
        Invoke-Api -Name "Ledger Validate (draft=no rows)" -Method POST -Path "/api/v1/ledger/validate" -Body $draftVal -Expected 404 | Out-Null
        Invoke-Api -Name "Issue DRAFT Invoice" -Method POST -Path "/api/v1/invoices/$DraftId/issue" -Expected 200 | Out-Null
        Invoke-Api -Name "Ledger Validate (after issue)" -Method POST -Path "/api/v1/ledger/validate" -Body $draftVal -Expected 200 | Out-Null
    }

    $idemKey = "smoke-pay-$ts"
    $idemBody = @{
        paymentNumber = "PAY-IDEM-$ts"
        customerId    = $CustomerId
        amount        = 111.00
        currencyCode  = "USD"
        paymentDate   = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
        method        = "BANK_TRANSFER"
        reference     = "IDEM-$ts"
        notes         = "idempotency smoke"
    } | ConvertTo-Json
    $p1 = Invoke-Api -Name "Payment Idempotent (1st)" -Method POST -Path "/api/v1/payments" -Body $idemBody -Expected 201 -IdempotencyKey $idemKey
    $p2 = Invoke-Api -Name "Payment Idempotent (2nd)" -Method POST -Path "/api/v1/payments" -Body $idemBody -Expected 201 -IdempotencyKey $idemKey
    $id1 = Get-JsonId $p1
    $id2 = Get-JsonId $p2
    if ($id1 -and $id2 -and $id1 -eq $id2) {
        Write-Host "[Assert same payment id] PASS ($id1)" -ForegroundColor Green
        $script:Pass++
    } else {
        Write-Host "FAIL: expected same payment id on replay ($id1 vs $id2)" -ForegroundColor Red
        $script:Fail++
    }
}

# --- Summary ---
Write-Host ""
Write-Host "=========================================="
Write-Host "PASS: $Pass  FAIL: $Fail"
Write-Host "=========================================="
if ($Fail -gt 0) { exit 1 } else { exit 0 }