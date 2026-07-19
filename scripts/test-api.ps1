# InvoiceGenie API Test Script (Windows PowerShell)
# Prefer profile=dev: mvn -pl ar-bootstrap quarkus:dev -Dquarkus.profile=dev
# Usage: ./scripts/test-api.ps1 [-BaseUrl http://localhost:8080]

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TenantId = $(if ($env:TENANT_ID) { $env:TENANT_ID } else { "11111111-1111-1111-1111-111111111111" })
)

$ErrorActionPreference = "Continue"
$Pass = 0
$Fail = 0

Write-Host "=========================================="
Write-Host "InvoiceGenie API Test Suite (PowerShell)"
Write-Host "Base URL: $BaseUrl"
Write-Host "Tenant:   $TenantId"
Write-Host "Profile tip: -Dquarkus.profile=dev (H2)"
Write-Host "=========================================="

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [string]$Body = $null,
        [int]$Expected = 200
    )
    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantId
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
for ($i = 1; $i -le 30; $i++) {
    try {
        $h = Invoke-WebRequest -Uri "$BaseUrl/q/health" -UseBasicParsing -TimeoutSec 2
        if ($h.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 1
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
Invoke-Api -Name "Get Customer" -Method GET -Path "/api/v1/customers/$CustomerId" -Expected 200 | Out-Null
Invoke-Api -Name "List Customers" -Method GET -Path "/api/v1/customers" -Expected 200 | Out-Null
Invoke-Api -Name "Customer Stats" -Method GET -Path "/api/v1/customers/stats" -Expected 200 | Out-Null

# --- Invoices ---
Write-Host ""
Write-Host "=== SECTION 2: INVOICE ===" -ForegroundColor Cyan
$invBody = @{
    invoiceNumber = "INV-TEST-$ts"
    customerId    = $CustomerId
    customerRef   = "API-Test"
    currencyCode  = "USD"
    dueDate       = "2026-04-30"
    lines         = @(
        @{ description = "Consulting"; amount = 5000.00 }
        @{ description = "Expenses"; amount = 500.00 }
    )
} | ConvertTo-Json -Depth 5
$invJson = Invoke-Api -Name "Create Invoice" -Method POST -Path "/api/v1/invoices" -Body $invBody -Expected 201
$InvoiceId = Get-JsonId $invJson
Write-Host "  Invoice ID: $InvoiceId"
Invoke-Api -Name "Get Invoice" -Method GET -Path "/api/v1/invoices/$InvoiceId" -Expected 200 | Out-Null
Invoke-Api -Name "List Invoices" -Method GET -Path "/api/v1/invoices?limit=10" -Expected 200 | Out-Null

# --- Payments (record + allocate) ---
Write-Host ""
Write-Host "=== SECTION 3: PAYMENTS ===" -ForegroundColor Cyan
$payBody = @{
    paymentNumber = "PAY-$ts"
    customerId    = $CustomerId
    amount        = 1000.00
    currencyCode  = "USD"
    paymentDate   = (Get-Date).ToUniversalTime().ToString("o")
    method        = "BANK_TRANSFER"
    reference     = "REF-$ts"
    notes         = "API test payment"
} | ConvertTo-Json
$payJson = Invoke-Api -Name "Record Payment" -Method POST -Path "/api/v1/payments" -Body $payBody -Expected 201
$PaymentId = Get-JsonId $payJson
Write-Host "  Payment ID: $PaymentId"
if ($PaymentId -and $InvoiceId) {
    $allocBody = @{
        allocatedBy = "11111111-1111-1111-1111-111111111111"
        allocations = @(@{ invoiceId = $InvoiceId; amount = 500.00; notes = "partial" })
    } | ConvertTo-Json -Depth 5
    Invoke-Api -Name "Manual Allocate" -Method POST -Path "/api/v1/payments/$PaymentId/allocate/manual" -Body $allocBody -Expected 200 | Out-Null
    Invoke-Api -Name "Get Allocations" -Method GET -Path "/api/v1/payments/$PaymentId/allocations" -Expected 200 | Out-Null
}

# --- Aging ---
Write-Host ""
Write-Host "=== SECTION 4: AGING ===" -ForegroundColor Cyan
Invoke-Api -Name "Aging Report" -Method GET -Path "/api/v1/aging" -Expected 200 | Out-Null
Invoke-Api -Name "Aging Buckets" -Method GET -Path "/api/v1/aging/buckets" -Expected 200 | Out-Null

# --- Cheques ---
Write-Host ""
Write-Host "=== SECTION 5: CHEQUES ===" -ForegroundColor Cyan
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
    Invoke-Api -Name "Deposit Cheque" -Method POST -Path "/api/v1/cheques/$ChequeId/deposit" -Expected 200 | Out-Null
    Invoke-Api -Name "Clear Cheque" -Method POST -Path "/api/v1/cheques/$ChequeId/clear" -Expected 200 | Out-Null
}

# --- Credit notes ---
Write-Host ""
Write-Host "=== SECTION 6: CREDIT NOTES ===" -ForegroundColor Cyan
if ($InvoiceId) {
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
Invoke-Api -Name "Outbox Pending" -Method GET -Path "/api/v1/outbox/pending?limit=10" -Expected 200 | Out-Null
Invoke-Api -Name "Process Outbox" -Method POST -Path "/api/v1/outbox/process" -Expected 200 | Out-Null

# --- Errors ---
Write-Host ""
Write-Host "=== SECTION 8: ERRORS ===" -ForegroundColor Cyan
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/v1/customers" -Method GET -Headers @{ "Content-Type" = "application/json" } -UseBasicParsing
    $code = [int]$resp.StatusCode
} catch {
    if (# InvoiceGenie API Test Script (Windows PowerShell)
# Prefer profile=dev: mvn -pl ar-bootstrap quarkus:dev -Dquarkus.profile=dev
# Usage: ./scripts/test-api.ps1 [-BaseUrl http://localhost:8080]

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TenantId = $(if ($env:TENANT_ID) { $env:TENANT_ID } else { "11111111-1111-1111-1111-111111111111" })
)

$ErrorActionPreference = "Continue"
$Pass = 0
$Fail = 0

Write-Host "=========================================="
Write-Host "InvoiceGenie API Test Suite (PowerShell)"
Write-Host "Base URL: $BaseUrl"
Write-Host "Tenant:   $TenantId"
Write-Host "Profile tip: -Dquarkus.profile=dev (H2)"
Write-Host "=========================================="

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [string]$Body = $null,
        [int]$Expected = 200
    )
    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantId
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
for ($i = 1; $i -le 30; $i++) {
    try {
        $h = Invoke-WebRequest -Uri "$BaseUrl/q/health" -UseBasicParsing -TimeoutSec 2
        if ($h.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 1
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
Invoke-Api -Name "Get Customer" -Method GET -Path "/api/v1/customers/$CustomerId" -Expected 200 | Out-Null
Invoke-Api -Name "List Customers" -Method GET -Path "/api/v1/customers" -Expected 200 | Out-Null
Invoke-Api -Name "Customer Stats" -Method GET -Path "/api/v1/customers/stats" -Expected 200 | Out-Null

# --- Invoices ---
Write-Host ""
Write-Host "=== SECTION 2: INVOICE ===" -ForegroundColor Cyan
$invBody = @{
    invoiceNumber = "INV-TEST-$ts"
    customerId    = $CustomerId
    customerRef   = "API-Test"
    currencyCode  = "USD"
    dueDate       = "2026-04-30"
    lines         = @(
        @{ description = "Consulting"; amount = 5000.00 }
        @{ description = "Expenses"; amount = 500.00 }
    )
} | ConvertTo-Json -Depth 5
$invJson = Invoke-Api -Name "Create Invoice" -Method POST -Path "/api/v1/invoices" -Body $invBody -Expected 201
$InvoiceId = Get-JsonId $invJson
Write-Host "  Invoice ID: $InvoiceId"
Invoke-Api -Name "Get Invoice" -Method GET -Path "/api/v1/invoices/$InvoiceId" -Expected 200 | Out-Null
Invoke-Api -Name "List Invoices" -Method GET -Path "/api/v1/invoices?limit=10" -Expected 200 | Out-Null

# --- Payments (record + allocate) ---
Write-Host ""
Write-Host "=== SECTION 3: PAYMENTS ===" -ForegroundColor Cyan
$payBody = @{
    paymentNumber = "PAY-$ts"
    customerId    = $CustomerId
    amount        = 1000.00
    currencyCode  = "USD"
    paymentDate   = (Get-Date).ToUniversalTime().ToString("o")
    method        = "BANK_TRANSFER"
    reference     = "REF-$ts"
    notes         = "API test payment"
} | ConvertTo-Json
$payJson = Invoke-Api -Name "Record Payment" -Method POST -Path "/api/v1/payments" -Body $payBody -Expected 201
$PaymentId = Get-JsonId $payJson
Write-Host "  Payment ID: $PaymentId"
if ($PaymentId -and $InvoiceId) {
    $allocBody = @{
        allocatedBy = "11111111-1111-1111-1111-111111111111"
        allocations = @(@{ invoiceId = $InvoiceId; amount = 500.00; notes = "partial" })
    } | ConvertTo-Json -Depth 5
    Invoke-Api -Name "Manual Allocate" -Method POST -Path "/api/v1/payments/$PaymentId/allocate/manual" -Body $allocBody -Expected 200 | Out-Null
    Invoke-Api -Name "Get Allocations" -Method GET -Path "/api/v1/payments/$PaymentId/allocations" -Expected 200 | Out-Null
}

# --- Aging ---
Write-Host ""
Write-Host "=== SECTION 4: AGING ===" -ForegroundColor Cyan
Invoke-Api -Name "Aging Report" -Method GET -Path "/api/v1/aging" -Expected 200 | Out-Null
Invoke-Api -Name "Aging Buckets" -Method GET -Path "/api/v1/aging/buckets" -Expected 200 | Out-Null

# --- Cheques ---
Write-Host ""
Write-Host "=== SECTION 5: CHEQUES ===" -ForegroundColor Cyan
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
    Invoke-Api -Name "Deposit Cheque" -Method POST -Path "/api/v1/cheques/$ChequeId/deposit" -Expected 200 | Out-Null
    Invoke-Api -Name "Clear Cheque" -Method POST -Path "/api/v1/cheques/$ChequeId/clear" -Expected 200 | Out-Null
}

# --- Credit notes ---
Write-Host ""
Write-Host "=== SECTION 6: CREDIT NOTES ===" -ForegroundColor Cyan
if ($InvoiceId) {
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
Invoke-Api -Name "Outbox Pending" -Method GET -Path "/api/v1/outbox/pending?limit=10" -Expected 200 | Out-Null
Invoke-Api -Name "Process Outbox" -Method POST -Path "/api/v1/outbox/process" -Expected 200 | Out-Null

# --- Errors ---
Write-Host ""
Write-Host "=== SECTION 8: ERRORS ===" -ForegroundColor Cyan
Invoke-Api -Name "Missing tenant" -Method GET -Path "/api/v1/customers" -Expected 400 | Out-Null
# force missing tenant by temporary empty - skip; covered by filter unit behaviour

Write-Host ""
Write-Host "=========================================="
Write-Host "Results: $Pass passed, $Fail failed"
Write-Host "=========================================="
exit $Fail.Exception.Response) { $code = [int]# InvoiceGenie API Test Script (Windows PowerShell)
# Prefer profile=dev: mvn -pl ar-bootstrap quarkus:dev -Dquarkus.profile=dev
# Usage: ./scripts/test-api.ps1 [-BaseUrl http://localhost:8080]

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TenantId = $(if ($env:TENANT_ID) { $env:TENANT_ID } else { "11111111-1111-1111-1111-111111111111" })
)

$ErrorActionPreference = "Continue"
$Pass = 0
$Fail = 0

Write-Host "=========================================="
Write-Host "InvoiceGenie API Test Suite (PowerShell)"
Write-Host "Base URL: $BaseUrl"
Write-Host "Tenant:   $TenantId"
Write-Host "Profile tip: -Dquarkus.profile=dev (H2)"
Write-Host "=========================================="

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [string]$Body = $null,
        [int]$Expected = 200
    )
    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantId
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
for ($i = 1; $i -le 30; $i++) {
    try {
        $h = Invoke-WebRequest -Uri "$BaseUrl/q/health" -UseBasicParsing -TimeoutSec 2
        if ($h.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 1
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
Invoke-Api -Name "Get Customer" -Method GET -Path "/api/v1/customers/$CustomerId" -Expected 200 | Out-Null
Invoke-Api -Name "List Customers" -Method GET -Path "/api/v1/customers" -Expected 200 | Out-Null
Invoke-Api -Name "Customer Stats" -Method GET -Path "/api/v1/customers/stats" -Expected 200 | Out-Null

# --- Invoices ---
Write-Host ""
Write-Host "=== SECTION 2: INVOICE ===" -ForegroundColor Cyan
$invBody = @{
    invoiceNumber = "INV-TEST-$ts"
    customerId    = $CustomerId
    customerRef   = "API-Test"
    currencyCode  = "USD"
    dueDate       = "2026-04-30"
    lines         = @(
        @{ description = "Consulting"; amount = 5000.00 }
        @{ description = "Expenses"; amount = 500.00 }
    )
} | ConvertTo-Json -Depth 5
$invJson = Invoke-Api -Name "Create Invoice" -Method POST -Path "/api/v1/invoices" -Body $invBody -Expected 201
$InvoiceId = Get-JsonId $invJson
Write-Host "  Invoice ID: $InvoiceId"
Invoke-Api -Name "Get Invoice" -Method GET -Path "/api/v1/invoices/$InvoiceId" -Expected 200 | Out-Null
Invoke-Api -Name "List Invoices" -Method GET -Path "/api/v1/invoices?limit=10" -Expected 200 | Out-Null

# --- Payments (record + allocate) ---
Write-Host ""
Write-Host "=== SECTION 3: PAYMENTS ===" -ForegroundColor Cyan
$payBody = @{
    paymentNumber = "PAY-$ts"
    customerId    = $CustomerId
    amount        = 1000.00
    currencyCode  = "USD"
    paymentDate   = (Get-Date).ToUniversalTime().ToString("o")
    method        = "BANK_TRANSFER"
    reference     = "REF-$ts"
    notes         = "API test payment"
} | ConvertTo-Json
$payJson = Invoke-Api -Name "Record Payment" -Method POST -Path "/api/v1/payments" -Body $payBody -Expected 201
$PaymentId = Get-JsonId $payJson
Write-Host "  Payment ID: $PaymentId"
if ($PaymentId -and $InvoiceId) {
    $allocBody = @{
        allocatedBy = "11111111-1111-1111-1111-111111111111"
        allocations = @(@{ invoiceId = $InvoiceId; amount = 500.00; notes = "partial" })
    } | ConvertTo-Json -Depth 5
    Invoke-Api -Name "Manual Allocate" -Method POST -Path "/api/v1/payments/$PaymentId/allocate/manual" -Body $allocBody -Expected 200 | Out-Null
    Invoke-Api -Name "Get Allocations" -Method GET -Path "/api/v1/payments/$PaymentId/allocations" -Expected 200 | Out-Null
}

# --- Aging ---
Write-Host ""
Write-Host "=== SECTION 4: AGING ===" -ForegroundColor Cyan
Invoke-Api -Name "Aging Report" -Method GET -Path "/api/v1/aging" -Expected 200 | Out-Null
Invoke-Api -Name "Aging Buckets" -Method GET -Path "/api/v1/aging/buckets" -Expected 200 | Out-Null

# --- Cheques ---
Write-Host ""
Write-Host "=== SECTION 5: CHEQUES ===" -ForegroundColor Cyan
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
    Invoke-Api -Name "Deposit Cheque" -Method POST -Path "/api/v1/cheques/$ChequeId/deposit" -Expected 200 | Out-Null
    Invoke-Api -Name "Clear Cheque" -Method POST -Path "/api/v1/cheques/$ChequeId/clear" -Expected 200 | Out-Null
}

# --- Credit notes ---
Write-Host ""
Write-Host "=== SECTION 6: CREDIT NOTES ===" -ForegroundColor Cyan
if ($InvoiceId) {
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
Invoke-Api -Name "Outbox Pending" -Method GET -Path "/api/v1/outbox/pending?limit=10" -Expected 200 | Out-Null
Invoke-Api -Name "Process Outbox" -Method POST -Path "/api/v1/outbox/process" -Expected 200 | Out-Null

# --- Errors ---
Write-Host ""
Write-Host "=== SECTION 8: ERRORS ===" -ForegroundColor Cyan
Invoke-Api -Name "Missing tenant" -Method GET -Path "/api/v1/customers" -Expected 400 | Out-Null
# force missing tenant by temporary empty - skip; covered by filter unit behaviour

Write-Host ""
Write-Host "=========================================="
Write-Host "Results: $Pass passed, $Fail failed"
Write-Host "=========================================="
exit $Fail.Exception.Response.StatusCode } else { $code = 0 }
}
Write-Host -NoNewline "[Missing tenant] GET /api/v1/customers ... "
if ($code -eq 400) {
    Write-Host "PASS ($code)" -ForegroundColor Green
    $Pass++
} else {
    Write-Host "FAIL (expected 400, got $code)" -ForegroundColor Red
    $Fail++
}

Write-Host ""
Write-Host "=========================================="
Write-Host "Results: $Pass passed, $Fail failed"
Write-Host "=========================================="
exit $Fail