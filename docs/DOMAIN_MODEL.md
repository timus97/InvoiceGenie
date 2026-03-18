# InvoiceGenie AR Domain Model — Aggregates & Business Logic

**Principles:** DDD (Domain-Driven Design), rich domain models, no JPA/Hibernate in domain.

---

## Aggregate Boundaries

An **aggregate** is a cluster of related objects treated as a single unit for data changes. Each aggregate has one **root** that enforces invariants and controls access to children.

### 1. Invoice Aggregate

**Root:** `Invoice`  
**Children:** `InvoiceLine` (1..N)  
**ID:** `InvoiceId` (UUID v7)

```
Invoice (root)
├── id: InvoiceId
├── invoiceNumber: String (tenant-scoped unique)
├── customerRef: String (denormalized display)
├── currencyCode: String (ISO 4217)
├── issueDate, dueDate: LocalDate
├── periodStart, periodEnd: LocalDate (optional billing period)
├── status: InvoiceStatus
├── notes, terms: String
├── createdAt, updatedAt, version
└── lines: List<InvoiceLine>
        ├── sequence: int
        ├── description: String
        ├── quantity: BigDecimal
        ├── unitPrice: Money
        ├── discountAmount: Money
        ├── taxRate: BigDecimal (nullable)
        ├── taxAmount: Money
        └── lineTotal: Money
```

**Invariants enforced by Invoice:**
- `invoiceNumber` immutable after creation
- `currencyCode` immutable; all lines must match
- At least 1 line required to `issue()`
- `getTotal() = subtotal + taxTotal` (discount already in line totals)
- Status transitions: `DRAFT → ISSUED → (PARTIALLY_PAID|PAID|OVERDUE)`, any → `CANCELLED|VOID`
- Lines are read-only after `ISSUED` (use credit memo for corrections)

**Business logic in Invoice:**
- `addLine(line)`, `removeLine(seq)` — only in DRAFT
- `setDueDate(date)`, `setPeriod(start,end)`, `setNotesAndTerms(...)` — only in DRAFT
- `issue()` — DRAFT → ISSUED, sets issuedAt
- `cancel()` — DRAFT/ISSUED only
- `voidInvoice()` — terminal state
- `isOpen()`, `isOverdue(today)` — read-only queries
- `applyPaymentStatus(fullyPaid)` — called by application layer after allocations

---

### 2. Payment Aggregate

**Root:** `Payment`  
**Children:** `PaymentAllocation` (0..N)  
**ID:** `PaymentId` (UUID v7)

```
Payment (root)
├── id: PaymentId
├── paymentNumber: String (tenant-scoped unique)
├── customerId: CustomerId
├── amount: Money (immutable)
├── paymentDate: LocalDate
├── receivedAt: Instant
├── method: PaymentMethod
├── reference: String (bank ref, check #)
├── bankAccountId: UUID (nullable)
├── notes: String
├── status: PaymentStatus
├── createdAt, updatedAt, version
└── allocations: List<PaymentAllocation>
        ├── id: UUID (internal)
        ├── invoiceId: InvoiceId
        ├── amount: Money
        ├── allocatedAt: Instant
        ├── allocatedBy: UUID (user/system)
        └── notes: String
```

**Invariants enforced by Payment:**
- `amount` immutable after creation
- `currencyCode` immutable; all allocations must match
- `amountUnallocated = amount - Σ(allocation.amount)` — never negative
- Allocations are immutable once created (no edit/delete of allocation)

**Business logic in Payment:**
- `allocate(invoiceId, amount, allocatedBy, notes)` — creates child, validates unallocated ≥ amount
- `addAllocation(existing)` — for reconstitution only
- `setNotes(...)` — only when RECEIVED
- `reverse()`, `refund()` — only when RECEIVED; app layer creates offsetting GL entries

---

### 3. Customer Aggregate

**Root:** `Customer`  
**Children:** none  
**ID:** `CustomerId` (UUID v7)

```
Customer (root)
├── id: CustomerId
├── customerCode: String (immutable, tenant-scoped unique)
├── legalName, displayName: String
├── email, phone: String
├── billingAddress: String (JSON)
├── currency: String (ISO 4217 default)
├── creditLimit: BigDecimal (nullable)
├── paymentTerms: String (NET30 etc.)
├── taxId: String
├── status: CustomerStatus
├── createdAt, updatedAt, version
└── (no child entities)
```

**Invariants enforced by Customer:**
- `customerCode` immutable after creation
- `currency` must be ISO 4217 (3-char)
- `creditLimit` ≥ 0 if set

**Business logic in Customer:**
- `updateDisplayName(name)`, `updateContact(...)`, `changeCurrency(...)`
- `setCreditLimit(limit)`, `setPaymentTerms(terms)`
- `block()`, `unblock()`, `delete()` — status transitions
- `canBeInvoiced()` — status == ACTIVE

---

## Domain Events

Events are immutable records published when aggregates change state. They cross aggregate boundaries and are consumed by external systems (GL, reporting).

| Event | Published By | Payload |
|-------|--------------|---------|
| `InvoiceIssued` | Invoice.issue() (via app layer) | tenantId, invoiceId, customerRef, total, dueDate |
| `PaymentRecorded` | Application service | tenantId, paymentId, customerRef, amount, paymentDate |
| `PaymentAllocated` | AllocationDomainService | tenantId, paymentId, invoiceId, amount |

Events implement `DomainEvent` (shared-kernel) with `eventId`, `tenantId`, `occurredAt`.

---

## Domain Services

Domain services contain logic that spans aggregates or doesn't belong to any single root.

### AllocationDomainService

**Why not in Payment or Invoice?**
- Payment knows its unallocated amount but not if Invoice is open
- Invoice knows its status but not Payment's unallocated amount
- Neither should reference the other (loose coupling)

**Responsibilities:**
- Validate invoice is open (ISSUED/PARTIALLY_PAID/OVERDUE)
- Validate currency match between payment and invoice
- Call `payment.allocate(...)` to create child
- Return `AllocationResult(allocation, PaymentAllocated event)`

**Not responsible for:**
- Persisting aggregates (repository port)
- Publishing events (event publisher port)
- Updating invoice amountDue (application layer computes from allocations)

---

## Where Business Logic Lives — Decision Matrix

| Logic | Location | Rationale |
|-------|----------|-----------|
| Invoice line totals | `InvoiceLine` constructor | Self-contained value object |
| Invoice totals sum | `Invoice.getTotal()` | Root owns children |
| Issue/cancel invoice | `Invoice.issue()`, `.cancel()` | State transition invariant |
| Payment allocation | `Payment.allocate()` | Payment owns allocations |
| Allocation cross-check | `AllocationDomainService` | Spans 2 aggregates |
| Credit limit check | Application layer | Needs external balance query |
| Amount due on invoice | Application layer | Computed from ledger/allocations |
| Overdue detection | `Invoice.isOverdue(today)` | Simple query; app layer schedules |
| GL journal entries | Application layer | Ledger is separate aggregate (future) |
| Audit log write | Application layer | Cross-cutting concern |

---

## Repository Contracts (Ports)

All repository methods require `TenantId` explicitly. No method without tenant.

| Port | Methods |
|------|---------|
| `InvoiceRepository` | save, findByTenantAndId, findByTenant (cursor page) |
| `PaymentRepository` | save, findByTenantAndId, findByTenantAndNumber, findUnallocatedByTenantAndCustomer, findAllocationsByTenantAndInvoice |
| `CustomerRepository` | save, findByTenantAndId, findByTenantAndCode, existsActive |

---

## Application Layer (Not Domain)

The following belong in `ar-application`, NOT domain:

- **Use cases:** `IssueInvoiceService`, `RecordPaymentService`, `ApplyAllocationService`
- **Transaction management:** One transaction per use case
- **Event publishing:** After commit via outbox
- **Validation of external refs:** e.g., customer exists and active
- **Audit log:** Write audit record after aggregate mutation
- **Authorization:** Check user permissions (outside domain)

---

## Module Structure

```
ar-domain/
├── model/
│   ├── invoice/
│   │   ├── Invoice.java           ← aggregate root
│   │   ├── InvoiceId.java
│   │   ├── InvoiceLine.java       ← child entity
│   │   ├── InvoiceStatus.java
│   │   └── InvoiceRepository.java ← port
│   ├── payment/
│   │   ├── Payment.java           ← aggregate root
│   │   ├── PaymentId.java
│   │   ├── PaymentAllocation.java ← child entity
│   │   ├── PaymentMethod.java
│   │   ├── PaymentStatus.java
│   │   └── PaymentRepository.java ← port
│   └── customer/
│       ├── Customer.java          ← aggregate root
│       ├── CustomerId.java
│       ├── CustomerStatus.java
│       └── CustomerRepository.java ← port
├── event/
│   ├── InvoiceIssued.java
│   ├── PaymentRecorded.java
│   └── PaymentAllocated.java
└── service/
    └── AllocationDomainService.java
```

---

## Testing Guidance

- Domain tests use no database, no Quarkus, no Spring
- Create aggregates in-memory, call methods, assert state/events
- Example: `Payment.allocate()` throws when unallocated < amount
- Use `AllocationDomainService` test to verify cross-aggregate rules
