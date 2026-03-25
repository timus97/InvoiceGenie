# InvoiceGenie AR Backend — Onboarding Guide

> **Welcome to the InvoiceGenie team!** This document provides a comprehensive guide to understanding the project, its architecture, domain concepts, and areas for improvement.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure & Modules](#3-project-structure--modules)
4. [Architecture & Design Principles](#4-architecture--design-principles)
5. [Domain Knowledge](#5-domain-knowledge)
6. [Multi-Tenancy & Tenant Isolation](#6-multi-tenancy--tenant-isolation)
7. [Key Workflows](#7-key-workflows)
8. [Database Schema](#8-database-schema)
9. [API Reference](#9-api-reference)
10. [Development Setup](#10-development-setup)
11. [Testing](#11-testing)
12. [Areas for Improvement](#12-areas-for-improvement)
13. [Further Reading](#13-further-reading)

---

## 1. Project Overview

**InvoiceGenie** is a **production-grade, multi-tenant Accounts Receivable (AR) backend** built with:

- **Java 17** + **Quarkus** framework
- **Domain-Driven Design (DDD)** principles
- **Hexagonal (Ports & Adapters) Architecture**
- Strict **multi-tenant isolation** for enterprise use

### What It Does

This system manages the **complete AR lifecycle**:

| Feature | Description |
|---------|-------------|
| **Invoice Management** | Create, issue, track, and manage invoices |
| **Payment Processing** | Record payments and allocate them to invoices |
| **Payment Allocation** | FIFO auto-allocation or manual allocation across invoices |
| **Double-Entry Ledger** | Built-in accounting with debit/credit validation |
| **Aging Reports** | Analyze AR by aging buckets (0-30, 31-60, 61-90, 90+ days) |
| **Cheque Processing** | Full cheque lifecycle (RECEIVED → DEPOSITED → CLEARED/BOUNCED) |
| **Credit Notes** | Handle early payment discounts and adjustments |
| **Customer Management** | Customer master data with credit limits and payment terms |

### Key Design Goals

- **Scale to millions of invoices** per tenant
- **Strict tenant isolation** — no data leakage between tenants
- **Extensible** for future AP (Accounts Payable) and GL (General Ledger) modules
- **Audit-first** design with full version history
- **Domain purity** — business logic isolated from infrastructure

---

## 2. Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Language** | Java | 17 |
| **Framework** | Quarkus | 3.6.4 |
| **Build Tool** | Maven | 3.x |
| **Database** | PostgreSQL 15+ | (SQLite for dev) |
| **ORM** | Hibernate (JPA) | via Quarkus |
| **Messaging** | Kafka (planned) | SmallRye Reactive |
| **API** | JAX-RS (REST) | Quarkus RESTEasy |
| **Docs** | OpenAPI/Swagger | MicroProfile OpenAPI |
| **Testing** | JUnit 5, Testcontainers | |

### Module Dependency Graph

```
shared-kernel (no dependencies)
      ↓
   ar-domain (depends on shared-kernel only)
      ↓
ar-application (depends on ar-domain only)
      ↓
┌─────────────────────────────────────┐
│ ar-adapter-api  ar-adapter-persistence  ar-adapter-messaging │
└─────────────────────────────────────┘
      ↓
ar-bootstrap (wires everything together)
```

---

## 3. Project Structure & Modules

```
/testbed/InvoiceGenie/
├── pom.xml                          # Root POM (parent for all modules)
├── README.md                        # User-facing documentation
├── docs/                            # Design & schema documentation
│   ├── AR_BACKEND_DESIGN.md
│   ├── DOMAIN_MODEL.md
│   ├── SCHEMA.md
│   └── sql/001_init_ar_schema.sql
├── docker-compose.yml               # PostgreSQL + App container
├── Dockerfile                       # Container build
├── scripts/test-api.sh              # API testing script
├── postman/                         # Postman collection for testing
│   ├── InvoiceGenie-AR.postman_collection.json
│   └── InvoiceGenie-local.postman_environment.json
│
├── shared-kernel/                   # ══════════════════════════════
│   └── src/main/java/com/invoicegenie/shared/
│       ├── domain/
│       │   ├── TenantId.java        # Value object for tenant identity
│       │   ├── Money.java           # Immutable money with currency
│       │   ├── EntityId.java        # Base entity ID type
│       │   └── DomainEvent.java     # Base domain event interface
│       └── tenant/
│           └── TenantContext.java   # ThreadLocal tenant holder
│
├── ar-domain/                       # ══════════════════════════════
│   └── src/main/java/com/invoicegenie/ar/domain/
│       ├── model/
│       │   ├── invoice/
│       │   │   ├── Invoice.java           # Aggregate root
│       │   │   ├── InvoiceId.java
│       │   │   ├── InvoiceLine.java       # Child entity
│       │   │   ├── InvoiceStatus.java     # Enum
│       │   │   ├── InvoiceRepository.java # Port (interface)
│       │   │   └── InvoiceLifecycleEngine.java
│       │   ├── payment/
│       │   │   ├── Payment.java
│       │   │   ├── PaymentId.java
│       │   │   ├── PaymentAllocation.java
│       │   │   ├── PaymentMethod.java
│       │   │   ├── PaymentStatus.java
│       │   │   └── PaymentRepository.java
│       │   ├── customer/
│       │   │   ├── Customer.java
│       │   │   ├── CustomerId.java
│       │   │   ├── CustomerStatus.java
│       │   │   └── CustomerRepository.java
│       │   └── ledger/
│       │       ├── Account.java
│       │       ├── AccountType.java
│       │       ├── EntryType.java
│       │       ├── LedgerEntry.java
│       │       └── LedgerRepository.java
│       ├── event/
│       │   ├── InvoiceIssued.java
│       │   ├── PaymentRecorded.java
│       │   └── PaymentAllocated.java
│       └── service/
│           ├── AgingService.java
│           ├── AllocationDomainService.java
│           ├── ChequeService.java
│           ├── CreditNoteService.java
│           ├── LedgerService.java
│           └── PaymentAllocationEngine.java
│
├── ar-application/                  # ══════════════════════════════
│   └── src/main/java/com/invoicegenie/ar/application/
│       ├── port/
│       │   ├── inbound/
│       │   │   ├── IssueInvoiceUseCase.java
│       │   │   ├── GetInvoiceUseCase.java
│       │   │   ├── ListInvoicesUseCase.java
│       │   │   ├── InvoiceLifecycleUseCase.java
│       │   │   └── PaymentAllocationUseCase.java
│       │   └── outbound/
│       │       ├── InvoiceRepository.java
│       │       ├── PaymentRepository.java
│       │       ├── CustomerRepository.java
│       │       ├── EventPublisher.java
│       │       └── IdGenerator.java
│       └── service/
│           ├── IssueInvoiceService.java
│           ├── GetInvoiceService.java
│           ├── ListInvoicesService.java
│           ├── InvoiceLifecycleService.java
│           └── PaymentAllocationService.java
│
├── ar-adapter-api/                  # ══════════════════════════════
│   └── src/main/java/com/invoicegenie/ar/adapter/api/
│       ├── filter/
│       │   ├── TenantFilter.java          # Resolves tenant from header
│       │   ├── TenantContextClearFilter.java
│       │   └── RequestLoggingFilter.java
│       └── rest/
│           ├── InvoiceResource.java
│           ├── PaymentResource.java
│           ├── PaymentAllocationProducer.java
│           ├── ChequeResource.java
│           ├── ChequeProducer.java
│           ├── LedgerResource.java
│           ├── LedgerProducer.java
│           ├── AgingResource.java
│           ├── AgingProducer.java
│           ├── CreditNoteResource.java
│           └── GlobalExceptionMapper.java
│
├── ar-adapter-persistence/          # ══════════════════════════════
│   └── src/main/java/com/invoicegenie/ar/adapter/persistence/
│       ├── entity/
│       │   ├── InvoiceEntity.java
│       │   ├── InvoiceLineEntity.java
│       │   ├── PaymentEntity.java
│       │   ├── PaymentAllocationEntity.java
│       │   └── CustomerEntity.java
│       ├── mapper/
│       │   ├── InvoiceMapper.java
│       │   ├── PaymentMapper.java
│       │   └── CustomerMapper.java
│       └── repository/
│           ├── InvoiceRepositoryAdapter.java
│           ├── PaymentRepositoryAdapter.java
│           ├── CustomerRepositoryAdapter.java
│           ├── LedgerRepositoryAdapter.java
│           ├── ChequeRepositoryAdapter.java
│           └── CreditNoteRepositoryAdapter.java
│
├── ar-adapter-messaging/            # ══════════════════════════════
│   └── src/main/java/com/invoicegenie/ar/adapter/messaging/
│       └── KafkaEventPublisher.java # Stub (no-op for dev)
│
└── ar-bootstrap/                    # ══════════════════════════════
    └── src/main/
        ├── java/com/invoicegenie/ar/
        │   └── ArApplication.java       # CDI wiring / bean producers
        └── resources/
            └── application.yml          # Quarkus config
```

---

## 4. Architecture & Design Principles

### Hexagonal Architecture (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────────────┐
│                     INBOUND (Driving / Primary)                  │
│   REST API (JAX-RS)  ←  TenantFilter  ←  RequestLoggingFilter   │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                 APPLICATION LAYER (Use Cases)                    │
│  IssueInvoiceService, GetInvoiceService, PaymentAllocationService│
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                  DOMAIN LAYER (Pure Business Logic)              │
│  Aggregates: Invoice, Payment, Customer, Ledger                 │
│  Domain Services: AllocationDomainService, LedgerService        │
│  Events: InvoiceIssued, PaymentRecorded, PaymentAllocated       │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                OUTBOUND PORTS (Interfaces)                       │
│  InvoiceRepository, PaymentRepository, EventPublisher,          │
│  IdGenerator, TenantContext                                     │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                OUTBOUND ADAPTERS (Infrastructure)                │
│  JPA/Hibernate (PostgreSQL), Kafka, UUID Generator              │
└─────────────────────────────────────────────────────────────────┘
```

### Key Rules

| Rule | Description |
|------|-------------|
| **Domain is pure** | `ar-domain` has ZERO dependencies on Quarkus, JPA, HTTP, or any framework |
| **Application has no infra** | `ar-application` defines ports; no adapter imports |
| **Adapters implement ports** | `ar-adapter-*` depend on `ar-application` and infrastructure |
| **Bootstrap wires everything** | `ar-bootstrap` is the CDI configuration hub |

---

## 5. Domain Knowledge

### Core Domain Concepts

#### Invoice Aggregate

An **Invoice** is the central aggregate representing money owed by a customer.

**Structure:**
```
Invoice (root)
├── id: InvoiceId (UUID v7)
├── invoiceNumber: String (tenant-scoped unique)
├── customerRef: String (denormalized display)
├── currencyCode: String (ISO 4217, e.g., "USD")
├── issueDate, dueDate: LocalDate
├── status: InvoiceStatus (DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, WRITTEN_OFF)
├── lines: List<InvoiceLine>
│   ├── sequence: int
│   ├── description: String
│   ├── quantity: BigDecimal
│   ├── unitPrice: Money
│   ├── discountAmount: Money
│   ├── taxRate: BigDecimal (nullable)
│   └── lineTotal: Money
└── totals: subtotal, taxTotal, discountTotal, total
```

**Invariants (enforced by domain):**
- `invoiceNumber` is immutable after creation
- `currencyCode` is immutable; all lines must match
- At least 1 line required to `issue()`
- `dueDate` cannot be before `issueDate`
- Lines are read-only after `ISSUED` (use credit memo for corrections)
- Terminal states: `PAID`, `WRITTEN_OFF`

**Status Flow:**
```
DRAFT ──issue()──► ISSUED ──┬──► PARTIALLY_PAID ──► PAID
                           │                          │
                           ├────────► OVERDUE ────────┤
                           │             │            │
                           │             ▼            │
                           │        WRITTEN_OFF ◄─────┘
                           │                          │
                           └────────► PAID ◄──────────┘
```

**Key Methods:**
- `issue()` — DRAFT → ISSUED (requires at least 1 line)
- `markOverdue(today)` — ISSUED/PARTIALLY_PAID → OVERDUE (when past due)
- `writeOff(reason)` — OVERDUE → WRITTEN_OFF (requires reason)
- `applyPaymentStatus(fullyPaid)` — Called by app layer after allocation
- `reopen(reason)` — PAID/PARTIALLY_PAID → ISSUED (for cheque bounce)

#### Payment Aggregate

A **Payment** represents money received from a customer.

**Structure:**
```
Payment (root)
├── id: PaymentId (UUID v7)
├── paymentNumber: String (tenant-scoped unique)
├── customerId: CustomerId
├── amount: Money (immutable)
├── paymentDate: LocalDate
├── receivedAt: Instant
├── method: PaymentMethod (BANK_TRANSFER, CARD, CASH, CHECK, OTHER)
├── reference: String (bank ref, check #)
├── status: PaymentStatus (RECEIVED, REVERSED, REFUNDED)
└── allocations: List<PaymentAllocation>
    ├── invoiceId: InvoiceId
    ├── amount: Money
    ├── allocatedAt: Instant
    ├── allocatedBy: UUID
    └── notes: String
```

**Invariants:**
- `amount` is immutable after creation
- `amountUnallocated = amount - Σ(allocation.amount)` — never negative
- Allocations are immutable once created
- All allocations must match payment currency

**Key Methods:**
- `allocate(invoiceId, amount, allocatedBy, notes)` — Creates child allocation
- `reverse()` — RECEIVED → REVERSED (app layer creates offsetting GL entries)
- `refund()` — RECEIVED → REFUNDED

#### Customer Aggregate

A **Customer** holds master data for AR customers.

**Key Fields:**
- `customerCode` — Immutable, tenant-scoped unique
- `currency` — Default currency for invoices
- `creditLimit` — Optional; if set, app layer may validate
- `paymentTerms` — e.g., "NET30", "NET60"
- `status` — ACTIVE, BLOCKED, DELETED

**Key Methods:**
- `block()`, `unblock()`, `delete()`
- `canBeInvoiced()` — Returns true if ACTIVE

#### Ledger (Double-Entry Accounting)

The system includes a built-in **double-entry ledger**:

| Account | Type | Description |
|---------|------|-------------|
| `AR` | ASSET | Accounts Receivable (money owed by customers) |
| `BANK` | ASSET | Bank account balance |
| `REVENUE` | REVENUE | Sales revenue |
| `EXPENSE` | EXPENSE | Operating expenses (bad debt, etc.) |

**Automatic Journal Entries:**

| Event | Debit | Credit |
|-------|-------|--------|
| Invoice Issued | AR | Revenue |
| Payment Received | BANK | AR |
| Write-off | EXPENSE | AR |

**Validation:** Every transaction must balance (`debits = credits`).

#### Payment Allocation

One payment can be allocated to **N invoices**; one invoice can receive allocations from **N payments**.

**Rules:**
- Currency must match between payment and invoice
- Allocation amount must be > 0
- Total allocations ≤ payment amount
- Invoice must be open (ISSUED, PARTIALLY_PAID, OVERDUE)

**AllocationDomainService:**
- Cross-aggregate logic (Payment ↔ Invoice)
- Neither aggregate should know about the other
- Validates invoice is open, currency matches
- Returns `AllocationResult(allocation, PaymentAllocated event)`

---

## 6. Multi-Tenancy & Tenant Isolation

### Why Multi-Tenancy Matters

This system serves **multiple companies (tenants)** from a single deployment. Each tenant's data must be **completely isolated**.

### Tenant Isolation Strategy

#### Database Layer

1. **Every table has `tenant_id UUID NOT NULL`**:
   ```sql
   CREATE TABLE ar_invoice (
       id UUID NOT NULL,
       tenant_id UUID NOT NULL REFERENCES ar_tenant(id),
       ...
       PRIMARY KEY (tenant_id, id)
   );
   ```

2. **Row-Level Security (RLS)** enabled on all tenant tables:
   ```sql
   ALTER TABLE ar_invoice ENABLE ROW LEVEL SECURITY;
   
   CREATE POLICY tenant_isolation ON ar_invoice
     USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
   ```

3. **All indexes lead with `(tenant_id, ...)`**:
   ```sql
   CREATE INDEX idx_invoice_tenant_status_due 
     ON ar_invoice(tenant_id, status, due_date);
   ```

#### Application Layer

1. **TenantFilter (JAX-RS filter)** — First filter in chain:
   ```java
   // Reads X-Tenant-Id header → sets TenantContext
   TenantId tenantId = resolveTenant(requestContext);
   TenantContext.setCurrentTenant(tenantId);
   ```

2. **TenantContext (ThreadLocal)** — Available throughout request:
   ```java
   public class TenantContext {
       private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();
       public static TenantId getCurrentTenant() { return CURRENT.get(); }
       public static void setCurrentTenant(TenantId tenantId) { ... }
   }
   ```

3. **All repository methods require TenantId**:
   ```java
   // ❌ WRONG
   invoiceRepository.findById(invoiceId);
   
   // ✅ CORRECT
   invoiceRepository.findByTenantAndId(tenantId, invoiceId);
   ```

4. **Outbox events include tenant_id header** for subscribers.

### Defence in Depth

- App-level filter sets tenant
- Repository methods require tenant parameter
- RLS policy at database level (even buggy SQL can't leak)

---

## 7. Key Workflows

### 1. Create & Issue Invoice

```
Client Request
     │
     ▼
TenantFilter (resolves X-Tenant-Id → TenantContext)
     │
     ▼
InvoiceResource.create()
     │
     ▼
IssueInvoiceService.issue(tenantId, command)
     │
     ├── Create Invoice (DRAFT)
     ├── Add InvoiceLines
     ├── invoice.issue() → DRAFT → ISSUED
     ├── invoiceRepository.save(tenantId, invoice)
     └── eventPublisher.publish(InvoiceIssued)
     │
     ▼
201 Created + Location header
```

### 2. Apply Payment (FIFO Allocation)

```
Client Request (POST /payments/{id}/allocate/fifo)
     │
     ▼
PaymentAllocationService.allocateFifo(tenantId, paymentId, allocatedBy)
     │
     ├── Load Payment + its unallocated amount
     ├── Find open invoices for customer (oldest first)
     ├── For each invoice:
     │   ├── AllocationDomainService.allocate()
     │   │   ├── Validate invoice open
     │   │   ├── Validate currency match
     │   │   └── payment.allocate(invoiceId, amount, ...)
     │   ├── paymentRepository.save()
     │   └── invoice.applyPaymentStatus(fullyPaid)
     │
     ▼
PaymentAllocated events published
```

### 3. Cheque Bounce (Payment Reversal)

```
Client Request (POST /cheques/{id}/bounce)
     │
     ▼
ChequeService.bounce(tenantId, chequeId, reason)
     │
     ├── Load Cheque (DEPOSITED status)
     ├── Validate state (must be DEPOSITED)
     ├── Change status → BOUNCED
     ├── Find related Payment
     ├── payment.reverse() → RECEIVED → REVERSED
     ├── Find affected Invoices
     ├── invoice.reopen(reason) → PAID → ISSUED
     ├── LedgerService.recordPaymentReversal() (Dr AR, Cr Bank)
     └── Save all changes
```

---

## 8. Database Schema

### Core Tables

| Table | Description |
|-------|-------------|
| `ar_tenant` | Tenant registry; base currency; settings |
| `ar_customer` | AR customer master (per-tenant) |
| `ar_invoice` | Invoice aggregate root (current state) |
| `ar_invoice_version` | Full JSONB snapshot per version change |
| `ar_invoice_line` | Line items (qty, unit_price, tax, discount) |
| `ar_payment` | Payment received; method, reference |
| `ar_payment_allocation` | Many-to-many: payment ↔ invoices |
| `ar_account` | Chart of accounts (AR, BANK, REVENUE, EXPENSE) |
| `ar_ledger_entry` | GL-style single-sided entries |
| `ar_audit_log` | Unified audit trail (before/after JSONB) |
| `ar_outbox` | Transactional outbox for domain events |

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **IDs** | UUID v7 (time-ordered) | No central bottleneck; sortable; good for partitioning |
| **Tenant Storage** | Single DB, `tenant_id` on every table | Simpler ops, one schema, easier backups |
| **Money Precision** | `NUMERIC(19,2)` | Standard 2-decimal; ISO 4217 |
| **Invoice Versioning** | Separate `ar_invoice_version` with full JSONB snapshot | Audit trail; no mutation loss |
| **Payment Allocation** | `ar_payment_allocation` (many-to-many) | Supports partial payments, overpayments |

### ER Diagram (Textual)

```
ar_tenant (1)
  ├── ar_customer (N)
  │     ├── ar_invoice (N)
  │     │     ├── ar_invoice_version (N)
  │     │     ├── ar_invoice_line (N)
  │     │     └── ar_ledger_entry (N)
  │     └── ar_payment (N)
  │           └── ar_payment_allocation (N) ──┐
  │                                             │
  └──── ar_account (N)                          │
          └── ar_ledger_entry (N) ◄─────────────┘

ar_audit_log (standalone, tenant-scoped)
ar_exchange_rate (standalone, tenant-scoped)
ar_outbox (standalone, tenant-scoped)
```

---

## 9. API Reference

### Base URL

- **PostgreSQL profile:** `http://localhost:8080`
- **SQLite profile:** `http://localhost:8081`

### Required Headers

```
Content-Type: application/json
X-Tenant-Id: <uuid>           # Required for ALL requests
Idempotency-Key: <unique-key> # Optional, for POST (prevents duplicates)
```

### Endpoints Summary

#### Invoices

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/invoices` | Create + issue invoice |
| `GET` | `/api/v1/invoices/{id}` | Get single invoice |
| `GET` | `/api/v1/invoices?limit=20&cursor=...&status=ISSUED` | List with pagination |
| `POST` | `/api/v1/invoices/{id}/issue` | Issue (DRAFT→ISSUED) |
| `POST` | `/api/v1/invoices/{id}/overdue?today=2026-01-01` | Mark overdue |
| `POST` | `/api/v1/invoices/{id}/writeoff` | Write off |
| `POST` | `/api/v1/invoices/{id}/payment` | Apply payment status |
| `PATCH` | `/api/v1/invoices/{id}/due-date` | Update due date |
| `DELETE` | `/api/v1/invoices/{id}` | 405 (use write-off) |

#### Payments & Allocation

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/payments/{id}/allocate/fifo` | Auto-allocate (oldest first) |
| `POST` | `/api/v1/payments/{id}/allocate/manual` | Manual allocation |
| `GET` | `/api/v1/payments/{id}/allocations` | Get allocations for payment |
| `GET` | `/api/v1/payments/invoices/{id}/allocations` | Get allocations for invoice |

#### Ledger

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/ledger/accounts` | List chart of accounts |
| `GET` | `/api/v1/ledger/balance/{account}` | Get balance for account |
| `GET` | `/api/v1/ledger/transactions/{id}` | Get entries for transaction |
| `GET` | `/api/v1/ledger/reference/{type}/{id}` | Get entries for invoice/payment |

#### Cheques

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/cheques` | Create cheque (RECEIVED) |
| `POST` | `/api/v1/cheques/{id}/deposit` | Deposit cheque |
| `POST` | `/api/v1/cheques/{id}/clear` | Clear cheque |
| `POST` | `/api/v1/cheques/{id}/bounce` | Bounce cheque |

#### Aging & Credit Notes

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/aging` | Generate aging report |
| `POST` | `/api/v1/aging/discount/calculate` | Calculate 2% early payment discount |
| `POST` | `/api/v1/credit-notes` | Generate credit note |
| `POST` | `/api/v1/credit-notes/{id}/apply` | Apply credit note to payment |

### Swagger UI

- **PostgreSQL:** `http://localhost:8080/q/swagger-ui/`
- **SQLite:** `http://localhost:8081/q/swagger-ui/`

---

## 10. Development Setup

### Prerequisites

- **Java 17+** (JDK)
- **Maven 3.8+**
- **PostgreSQL 15+** (for full dev) OR use SQLite profile

### Quick Start

```bash
# 1. Clone and build
cd /testbed/InvoiceGenie
mvn clean install -DskipTests

# 2. Start PostgreSQL (optional, for persistence)
docker-compose up -d

# 3. Run with PostgreSQL (default)
mvn -pl ar-bootstrap quarkus:dev

# 4. Run with SQLite (no Postgres needed)
mvn -pl ar-bootstrap -Dquarkus.profile=sqlite quarkus:dev
```

### Configuration

**application.yml** (ar-bootstrap/src/main/resources/):

```yaml
quarkus:
  application:
    name: invoice-genie-ar
  http:
    port: 8080
  datasource:
    db-kind: postgresql
    jdbc:
      url: jdbc:postgresql://localhost:5432/invoicegenie
      max-size: 32
  hibernate-orm:
    database:
      generation: none  # Use migrations in production
```

**SQLite profile** (dev fallback):
- Uses H2 in-memory database
- Listens on port 8081
- Auto-creates schema

---

## 11. Testing

### Running Tests

```bash
# All tests
mvn test

# Persistence adapter tests only
mvn -pl ar-adapter-persistence test

# Domain lifecycle tests only
mvn -pl ar-domain test
```

### Test Structure

| Module | Test Location | Purpose |
|--------|---------------|---------|
| `ar-domain` | `src/test/java/.../InvoiceLifecycleEngineTest.java` | Pure domain tests (no DB) |
| `ar-adapter-persistence` | `src/test/java/.../repository/` | JPA integration tests |

### Domain Test Example

```java
// No database, no Quarkus, no Spring
@Test
void paymentAllocate_throwsWhenExceedsUnallocated() {
    Payment payment = new Payment(...);
    Money amount = Money.of("100.00", "USD");
    
    // Act & Assert
    assertThrows(IllegalStateException.class, () -> {
        payment.allocate(invoiceId, amount, userId, null);
    });
}
```

### API Testing Script

```bash
# Make executable
chmod +x scripts/test-api.sh

# Run against local dev server
./scripts/test-api.sh http://localhost:8080

# With custom tenant
TENANT_ID=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee ./scripts/test-api.sh
```

---

## 12. Areas for Improvement

Based on code review, here are **potential improvements** and **missing parts**:

### High Priority

| Area | Status | Notes |
|------|--------|-------|
| **Event Publishing** | ✅ DONE | `KafkaEventPublisher` uses transactional outbox pattern |
| **Outbox Pattern** | ✅ DONE | `OutboxWorker` polls `ar_outbox` and publishes events with retry logic |
| **Audit Logging** | ✅ DONE | `AuditEntry`, `AuditLogEntity`, `AuditRepository` integrated in application services |
| **Customer Repository** | ✅ DONE | `CustomerRepositoryAdapter` implemented with full CRUD |
| **Credit Note Repository** | ✅ DONE | `CreditNoteRepositoryAdapter` implemented |
| **Cheque Repository** | ✅ DONE | `ChequeRepositoryAdapter` implemented |
| **Invoice Versioning** | ❌ MISSING | Schema supports `ar_invoice_version` but not persisted from app layer |
| **RLS Session Variable** | ✅ DONE | `TenantFilter` sets `app.current_tenant_id` via `set_config()` |
| **IdGenerator (UUID v7)** | ✅ DONE | `UuidV7` generates time-ordered UUIDs for better index locality |

### Medium Priority

| Area | Status | Notes |
|------|--------|-------|
| **Money Class** | ❌ MISSING | No `multiply`, `divide`, `percentage` methods for discount/tax calculations |
| **Currency Conversion** | ❌ MISSING | Exchange rate table exists but not used in LedgerService |
| **Payment Reversal** | ❌ MISSING | Domain has `reverse()` but app layer doesn't wire it |
| **Dunning (Overdue Reminders)** | ❌ MISSING | Not implemented - future schedule-based notifications |
| **Reporting** | ⚠️ PARTIAL | Aging report exists; other reports (cash forecast) missing |
| **Idempotency** | ⚠️ PARTIAL | Implemented in `PaymentAllocationService` but not enforced globally |
| **Input Validation** | ❌ MISSING | DTOs use records but no Bean Validation annotations |
| **Error Handling** | ⚠️ PARTIAL | `GlobalExceptionMapper` exists; no correlation IDs |

### Low Priority / Nice to Have

| Area | Status | Notes |
|------|--------|-------|
| **OpenAPI Schemas** | ⚠️ PARTIAL | DTOs are records; could add `@Schema` annotations |
| **Pagination Cursor** | ✅ DONE | Cursor-based pagination implemented |
| **Health Checks** | ❌ MISSING | Basic health exists; no custom readiness checks |
| **Metrics** | ❌ MISSING | No Micrometer counters for business metrics |
| **Caching** | ❌ MISSING | No caching layer for tenant metadata |
| **Dockerfile** | ⚠️ PARTIAL | Basic build; could optimize with multi-stage |
| **CI/CD** | ❌ MISSING | No pipeline visible |

### Missing Tests

| Test Type | Status | Notes |
|-----------|--------|-------|
| Payment allocation cross-aggregate | ✅ DONE | `PaymentAllocationEngineTest`, `PaymentAllocationServiceTest` |
| LedgerService double-entry validation | ✅ DONE | `LedgerServiceTest` |
| Cheque lifecycle | ✅ DONE | `ChequeServiceTest`, `ChequeTest` |
| Aging report calculation | ✅ DONE | `AgingServiceTest`, `AgingBucketTest`, `AgingReportTest` |
| Multi-tenant isolation | ❌ MISSING | **Critical!** No tests verifying tenant data isolation |
| Concurrency tests | ❌ MISSING | No tests for concurrent payment allocation |

### Security Considerations

| Area | Status | Notes |
|------|--------|-------|
| **Authentication** | ❌ MISSING | Only `X-Tenant-Id` header — no JWT validation |
| **Authorization** | ❌ MISSING | No per-endpoint role checks (`@RolesAllowed`) |
| **Rate Limiting** | ❌ MISSING | Not implemented |
| **Input Sanitization** | ⚠️ PARTIAL | JPA parameterization prevents SQL injection; DTOs lack validation |

---

### Recommended Next Steps (Priority Order)

1. **Invoice Versioning** - Persist version snapshots in `ar_invoice_version` on every invoice update
2. **Multi-Tenant Isolation Tests** - Add integration tests that verify tenant data cannot leak
3. **Input Validation** - Add Bean Validation annotations to DTOs (`@NotNull`, `@Size`, `@Pattern`)
4. **Payment Reversal Workflow** - Wire `Payment.reverse()` in application layer
5. **Money Arithmetic** - Add `multiply()`, `divide()`, `percentage()` methods
6. **Correlation IDs** - Add request correlation IDs for tracing and error handling
7. **Health Checks** - Add custom readiness/liveness checks for DB connectivity
8. **Metrics** - Add Micrometer counters for business events
9. **Authentication** - Integrate JWT validation in `TenantFilter`
10. **Rate Limiting** - Add rate limiting for API protection

---

## 13. Further Reading

### Project Documentation

| Document | Path | Purpose |
|----------|------|---------|
| README | `README.md` | Quick start, API examples, test scenarios |
| Architecture | `docs/AR_BACKEND_DESIGN.md` | Detailed design decisions, scale notes |
| Domain Model | `docs/DOMAIN_MODEL.md` | Aggregates, business rules, invariants |
| Database Schema | `docs/SCHEMA.md` | Full SQL schema, ER diagram, RLS policies |
| Outbox Pattern | `docs/OUTBOX_PATTERN.md` | Transactional outbox implementation guide |

### External Resources

- **Quarkus Guide**: https://quarkus.io/guides/
- **Domain-Driven Design**: https://martinfowler.com/bliki/DomainDrivenDesign.html
- **Hexagonal Architecture**: https://alistair.cockburn.us/hexagonal-architecture/
- **UUID v7**: https://datatracker.ietf.org/doc/html/draft-peabody-dispatch-new-uuid-format

---

## Appendix A: Quick Reference Card

### Essential Commands

```bash
# Build
mvn clean install -DskipTests

# Dev (PostgreSQL)
mvn -pl ar-bootstrap quarkus:dev

# Dev (SQLite, no DB)
mvn -pl ar-bootstrap -Dquarkus.profile=sqlite quarkus:dev

# Tests
mvn test

# Run API test script
TENANT_ID=00000000-0000-0000-0000-000000000001 ./scripts/test-api.sh
```

### Key Classes to Know

| Concept | Class |
|---------|-------|
| Tenant ID | `shared-kernel/.../TenantId.java` |
| Money | `shared-kernel/.../Money.java` |
| Invoice Aggregate | `ar-domain/.../invoice/Invoice.java` |
| Invoice Lifecycle | `ar-domain/.../invoice/InvoiceLifecycleEngine.java` |
| Payment Aggregate | `ar-domain/.../payment/Payment.java` |
| Allocation Logic | `ar-domain/.../service/AllocationDomainService.java` |
| Ledger Entries | `ar-domain/.../service/LedgerService.java` |
| Outbox Entry | `ar-domain/.../outbox/OutboxEntry.java` |
| Outbox Repository | `ar-domain/.../outbox/OutboxRepository.java` |
| Tenant Resolution | `ar-adapter-api/.../filter/TenantFilter.java` |
| REST Endpoints | `ar-adapter-api/.../rest/InvoiceResource.java` |
| JPA Adapter | `ar-adapter-persistence/.../repository/InvoiceRepositoryAdapter.java` |
| Outbox Adapter | `ar-adapter-persistence/.../repository/OutboxRepositoryAdapter.java` |
| Event Publisher | `ar-adapter-messaging/.../KafkaEventPublisher.java` |
| Outbox Worker | `ar-adapter-messaging/.../OutboxWorker.java` |
| CDI Wiring | `ar-bootstrap/.../ArApplication.java` |

---

*Document generated for InvoiceGenie AR Backend onboarding. Last updated: 2026-03-24*

