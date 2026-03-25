# Transactional Outbox Pattern — Implementation Guide

> This document records the implementation of the **Transactional Outbox Pattern** in InvoiceGenie AR Backend, including all modifications, workflow, and usage guidelines.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Problem Statement](#2-problem-statement)
3. [Solution Architecture](#3-solution-architecture)
4. [Implementation Details](#4-implementation-details)
5. [Files Modified/Created](#5-files-modifiedcreated)
6. [Configuration](#6-configuration)
7. [Workflow Diagrams](#7-workflow-diagrams)
8. [Usage Examples](#8-usage-examples)
9. [Testing](#9-testing)
10. [Monitoring & Operations](#10-monitoring--operations)
11. [Future Improvements](#11-future-improvements)

---

## 1. Overview

The **Transactional Outbox Pattern** ensures reliable event publishing in distributed systems. Instead of publishing events directly to a message broker (which can fail independently of database transactions), events are first saved to an outbox table within the same database transaction as the aggregate changes. A separate worker then polls this table and publishes events to the message broker.

### Key Benefits

| Benefit | Description |
|---------|-------------|
| **Reliability** | Events are never lost — they're persisted atomically with aggregate changes |
| **Ordering** | Events are published in the order they were created (FIFO) |
| **Retry** | Failed events are automatically retried with configurable limits |
| **Tenant Isolation** | Every event includes tenant_id for subscriber filtering |
| **Audit Trail** | Complete history of all events and their publishing status |

---

## 2. Problem Statement

### The Dual-Write Problem

Without the outbox pattern, the application must write to two systems:

```
┌─────────────────────────────────────────────────────────────────┐
│                     Transaction                                 │
│  ┌───────────────┐         ┌───────────────┐                   │
│  │ Save Invoice  │────────▶│ Publish Event │                   │
│  │ to PostgreSQL │         │ to Kafka      │                   │
│  └───────────────┘         └───────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

**What can go wrong?**

| Scenario | Result |
|----------|--------|
| DB succeeds, Kafka fails | Invoice saved but event lost → GL never gets it |
| Kafka succeeds, DB fails | Event published but invoice not saved → Inconsistent |
| Network hiccup mid-transaction | Partial state, hard to recover |

**The core issue:** You cannot atomically write to two different systems.

---

## 3. Solution Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Application Transaction                          │
│  ┌──────────────────┐         ┌──────────────────────┐                  │
│  │ Save Invoice     │────────▶│ Insert into ar_outbox│                  │
│  │ to ar_invoice    │         │ (same DB, same TX)   │                  │
│  └──────────────────┘         └──────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼ (committed atomically)
┌─────────────────────────────────────────────────────────────────────────┐
│                          Outbox Worker (async)                           │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ 1. SELECT * FROM ar_outbox WHERE status = 'PENDING'                │  │
│  │ 2. For each row: publish to Kafka                                  │  │
│  │ 3. UPDATE ar_outbox SET status = 'PUBLISHED' WHERE id = ?          │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              Kafka                                       │
│  Topic: ar.domain.events                                                │
│  Headers: tenant_id, event_type                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Overview

| Component | Module | Responsibility |
|-----------|--------|----------------|
| `OutboxEntry` | ar-domain | Domain model for an event waiting to be published |
| `OutboxStatus` | ar-domain | Enum: PENDING, PROCESSING, PUBLISHED, FAILED |
| `OutboxRepository` | ar-domain | Port (interface) for outbox persistence |
| `OutboxEntity` | ar-adapter-persistence | JPA entity mapping to `ar_outbox` table |
| `OutboxRepositoryAdapter` | ar-adapter-persistence | Implementation of OutboxRepository |
| `KafkaEventPublisher` | ar-adapter-messaging | Saves events to outbox (not direct to Kafka) |
| `OutboxWorker` | ar-adapter-messaging | Scheduled worker that polls and publishes |

---

## 4. Implementation Details

### 4.1 Domain Layer (Pure Java — No Framework Dependencies)

#### OutboxEntry.java

```java
public final class OutboxEntry {
    private final UUID id;
    private final TenantId tenantId;
    private final String aggregateType;  // INVOICE, PAYMENT, CUSTOMER
    private final UUID aggregateId;
    private final String eventType;      // InvoiceIssued, PaymentRecorded, etc.
    private final String payload;        // JSON representation
    private final Instant createdAt;
    private Instant publishedAt;
    private OutboxStatus status;
    private int retryCount;
    private String lastError;

    // Business methods
    public OutboxEntry markProcessing() { ... }
    public OutboxEntry markPublished() { ... }
    public OutboxEntry markFailed(String error) { ... }
    public boolean canRetry() { ... }
    public String getTopicName() { ... }
}
```

#### OutboxStatus.java

```java
public enum OutboxStatus {
    PENDING,      // Ready to be published
    PROCESSING,   // Currently being processed by worker
    PUBLISHED,    // Successfully published to Kafka
    FAILED        // Failed after max retries
}
```

#### OutboxRepository.java (Port)

```java
public interface OutboxRepository {
    void save(TenantId tenantId, OutboxEntry entry);
    List<OutboxEntry> findPending(int limit);
    Optional<OutboxEntry> findById(UUID id);
    void update(OutboxEntry entry);
    int deletePublishedOlderThan(Instant olderThan);
    long countByStatus(OutboxStatus status);
}
```

### 4.2 Persistence Layer

#### OutboxEntity.java (JPA)

Maps to the `ar_outbox` table:

```java
@Entity
@Table(name = "ar_outbox")
public class OutboxEntity {
    @Id
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;
    
    // ... additional fields: createdAt, publishedAt, retryCount, lastError
}
```

#### OutboxRepositoryAdapter.java

Implements the port using JPA EntityManager:

```java
@ApplicationScoped
public class OutboxRepositoryAdapter implements OutboxRepository {
    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(TenantId tenantId, OutboxEntry entry) {
        OutboxEntity entity = toEntity(tenantId, entry);
        em.persist(entity);
    }

    @Override
    public List<OutboxEntry> findPending(int limit) {
        return em.createQuery(
            "SELECT e FROM OutboxEntity e WHERE e.status = :status ORDER BY e.createdAt",
            OutboxEntity.class)
            .setParameter("status", OutboxStatus.PENDING)
            .setMaxResults(limit)
            .getResultList()
            .stream()
            .map(this::toDomain)
            .toList();
    }
    // ... other methods
}
```

### 4.3 Messaging Layer

#### KafkaEventPublisher.java

**Key Change:** Instead of publishing directly to Kafka, it saves to the outbox:

```java
@ApplicationScoped
public class KafkaEventPublisher implements EventPublisher {
    
    @Inject
    OutboxRepository outboxRepository;

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        OutboxEntry entry = createOutboxEntry(event);
        outboxRepository.save(event.tenantId(), entry);
        
        LOG.debugf("Saved event to outbox: type=%s, id=%s", 
                entry.getEventType(), entry.getId());
    }

    private OutboxEntry createOutboxEntry(DomainEvent event) {
        return new OutboxEntry(
                event.tenantId(),
                getAggregateType(event),
                getAggregateId(event),
                event.getClass().getSimpleName(),
                serializeEvent(event)
        );
    }
}
```

#### OutboxWorker.java

Scheduled worker that polls and publishes:

```java
@ApplicationScoped
public class OutboxWorker {

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    @Channel("outbox-events")
    Emitter<String> eventEmitter;

    @Scheduled(every = "${outbox.poll-interval:5s}")
    @Transactional
    public void processPendingEvents() {
        List<OutboxEntry> pending = outboxRepository.findPending(batchSize);
        
        for (OutboxEntry entry : pending) {
            try {
                entry.markProcessing();
                outboxRepository.update(entry);

                eventEmitter.send(entry.getPayload());

                entry.markPublished();
                outboxRepository.update(entry);

            } catch (Exception e) {
                entry.markFailed(e.getMessage());
                outboxRepository.update(entry);
            }
        }
    }

    @Scheduled(cron = "${outbox.cleanup-cron:0 0 0 * * ?}")
    @Transactional
    public void cleanupOldEntries() {
        Instant cutoff = Instant.now().minus(cleanupDays, ChronoUnit.DAYS);
        outboxRepository.deletePublishedOlderThan(cutoff);
    }
}
```

---

## 5. Files Modified/Created

### New Files Created

| File | Module | Description |
|------|--------|-------------|
| `ar-domain/.../outbox/OutboxEntry.java` | ar-domain | Domain model for outbox entry |
| `ar-domain/.../outbox/OutboxStatus.java` | ar-domain | Status enum |
| `ar-domain/.../outbox/OutboxRepository.java` | ar-domain | Port (interface) |
| `ar-adapter-persistence/.../entity/OutboxEntity.java` | ar-adapter-persistence | JPA entity |
| `ar-adapter-persistence/.../repository/OutboxRepositoryAdapter.java` | ar-adapter-persistence | Adapter implementation |
| `ar-adapter-messaging/.../OutboxWorker.java` | ar-adapter-messaging | Scheduled worker |

### Files Modified

| File | Changes |
|------|---------|
| `ar-adapter-messaging/.../KafkaEventPublisher.java` | Changed from no-op stub to outbox-based publisher |
| `ar-application/.../EventPublisher.java` | Updated interface to accept `DomainEvent` instead of specific event types |
| `ar-domain/.../event/InvoiceIssued.java` | Added `eventId` field and implemented `DomainEvent` interface |
| `ar-application/.../IssueInvoiceService.java` | Updated to use new InvoiceIssued constructor |
| `ar-bootstrap/.../ArApplication.java` | Removed no-op EventPublisher stub (CDI now injects KafkaEventPublisher) |
| `ar-bootstrap/.../application.yml` | Added outbox configuration |
| `ar-adapter-messaging/pom.xml` | Added scheduler and reactive messaging dependencies |

---

## 6. Configuration

### application.yml

```yaml
# Outbox events channel for the OutboxWorker
mp.messaging.outgoing.outbox-events:
  connector: smallrye-kafka
  topic: ar.domain.events
  value.serializer: org.apache.kafka.common.serialization.StringSerializer
  key.serializer: org.apache.kafka.common.serialization.StringSerializer

# Transactional Outbox Configuration
outbox:
  enabled: true              # Enable/disable the worker
  batch-size: 100            # Max events per poll
  poll-interval: 5s          # How often to poll
  cleanup-days: 7            # Delete published events older than X days
  cleanup-cron: "0 0 0 * * ?" # Daily at midnight
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `outbox.enabled` | `true` | Enable/disable the outbox worker |
| `outbox.batch-size` | `100` | Maximum events to process per poll |
| `outbox.poll-interval` | `5s` | Polling interval (Quarkus duration format) |
| `outbox.cleanup-days` | `7` | Days to keep published events before cleanup |
| `outbox.cleanup-cron` | `0 0 0 * * ?` | Cron expression for cleanup job |

---

## 7. Workflow Diagrams

### 7.1 Invoice Creation with Event Publishing

```
Client Request (POST /api/v1/invoices)
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ TenantFilter                                                     │
│   - Extract X-Tenant-Id header                                   │
│   - Set TenantContext                                            │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ InvoiceResource.create()                                         │
│   - Validate request                                             │
│   - Build IssueInvoiceCommand                                    │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ IssueInvoiceService.issue()                                      │
│   ┌───────────────────────────────────────────────────────────┐  │
│   │ Transaction:                                               │  │
│   │   1. Create Invoice aggregate (DRAFT)                     │  │
│   │   2. invoice.issue() → DRAFT → ISSUED                     │  │
│   │   3. invoiceRepository.save() → INSERT ar_invoice         │  │
│   │   4. eventPublisher.publish(InvoiceIssued)                │  │
│   │      └── KafkaEventPublisher.publish()                    │  │
│   │          └── outboxRepository.save() → INSERT ar_outbox   │  │
│   └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
201 Created + Invoice ID
         │
         │ (async, separate thread)
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ OutboxWorker.processPendingEvents()                              │
│   - Poll ar_outbox WHERE status = 'PENDING'                      │
│   - For each entry:                                              │
│     1. Mark PROCESSING                                           │
│     2. Send to Kafka (topic: ar.domain.events)                   │
│     3. Mark PUBLISHED                                            │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ Kafka (topic: ar.domain.events)                                  │
│   - Message: JSON event payload                                  │
│   - Headers: tenant_id, event_type                               │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ Subscribers (GL, Reporting, Dunning)                             │
│   - Filter by tenant_id header                                   │
│   - Process event                                                │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 Event Status Transitions

```
┌─────────┐     Worker picks up      ┌────────────┐
│ PENDING │ ───────────────────────▶ │ PROCESSING │
└─────────┘                          └────────────┘
     │                                     │
     │                                     │
     │ (retry after failure)               │
     │                                     │
     │         ┌───────────────────────────┼───────────────────────────┐
     │         │                           │                           │
     │         ▼                           ▼                           ▼
     │   ┌──────────┐               ┌───────────┐               ┌────────┐
     └───│ PENDING  │               │ PUBLISHED │               │ FAILED │
         │(retry++) │               │ (success) │               │(max 5) │
         └──────────┘               └───────────┘               └────────┘
              │                           │
              │                           │
              │                           ▼
              │                    ┌──────────────┐
              │                    | Cleanup Job  │
              │                    | (delete old) │
              │                    └──────────────┘
              │
              └──────────▶ (retried by worker)
```

---

## 8. Usage Examples

### 8.1 Publishing an Event (Application Service)

```java
// In your application service
public class IssueInvoiceService implements IssueInvoiceUseCase {
    
    private final EventPublisher eventPublisher;

    public InvoiceId issue(TenantId tenantId, IssueInvoiceCommand command) {
        // ... create and save invoice ...
        
        // Publish event - this saves to outbox, not directly to Kafka
        eventPublisher.publish(new InvoiceIssued(
            tenantId,
            invoice.getId(),
            invoice.getCustomerRef(),
            invoice.getTotal(),
            invoice.getDueDate()
        ));
        
        return invoice.getId();
    }
}
```

### 8.2 Checking Outbox Status (Admin/Operations)

```java
// Health check or metrics endpoint
@Inject OutboxWorker outboxWorker;

public OutboxMetrics getMetrics() {
    return new OutboxMetrics(
        outboxWorker.getPendingCount(),
        outboxWorker.getFailedInDbCount(),
        outboxWorker.getPublishedCount(),
        outboxWorker.getFailedCount()
    );
}
```

### 8.3 Querying Failed Events (Manual Intervention)

```sql
-- Find failed events
SELECT id, event_type, payload, retry_count, last_error, created_at
FROM ar_outbox
WHERE status = 'FAILED'
ORDER BY created_at DESC;

-- Retry a failed event manually
UPDATE ar_outbox 
SET status = 'PENDING', retry_count = 0, last_error = NULL
WHERE id = '<event-id>';
```

---

## 9. Testing

### 9.1 Unit Tests for Domain Model

```java
class OutboxEntryTest {
    
    @Test
    void shouldMarkAsPublished() {
        OutboxEntry entry = new OutboxEntry(tenantId, "INVOICE", invoiceId, 
                                            "InvoiceIssued", "{}");
        
        entry.markPublished();
        
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(entry.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldMarkFailedAndAllowRetry() {
        OutboxEntry entry = new OutboxEntry(tenantId, "INVOICE", invoiceId, 
                                            "InvoiceIssued", "{}");
        
        entry.markFailed("Connection refused");
        
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entry.getRetryCount()).isEqualTo(1);
        assertThat(entry.canRetry()).isTrue();
    }

    @Test
    void shouldMarkFailedPermanentlyAfterMaxRetries() {
        OutboxEntry entry = new OutboxEntry(tenantId, "INVOICE", invoiceId, 
                                            "InvoiceIssued", "{}");
        
        for (int i = 0; i < 5; i++) {
            entry.markFailed("Error");
        }
        
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(entry.canRetry()).isFalse();
    }
}
```

### 9.2 Integration Tests

```java
@QuarkusTest
class OutboxWorkerIT {

    @Inject
    OutboxRepository outboxRepository;

    @Test
    void shouldSaveAndRetrievePendingEvents() {
        OutboxEntry entry = new OutboxEntry(tenantId, "INVOICE", invoiceId, 
                                            "InvoiceIssued", "{\"test\": true}");
        
        outboxRepository.save(tenantId, entry);
        
        List<OutboxEntry> pending = outboxRepository.findPending(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEventType()).isEqualTo("InvoiceIssued");
    }
}
```

---

## 10. Monitoring & Operations

### 10.1 Metrics to Track

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `outbox.pending.count` | Events waiting to be published | > 100 |
| `outbox.failed.count` | Events that failed permanently | > 0 |
| `outbox.published.total` | Total events published | - |
| `outbox.latency.avg` | Avg time from creation to publish | > 30s |

### 10.2 Health Check Endpoint

```java
@Path("/api/v1/outbox")
public class OutboxResource {

    @Inject OutboxWorker outboxWorker;

    @GET
    @Path("/health")
    public Response health() {
        long pending = outboxWorker.getPendingCount();
        long failed = outboxWorker.getFailedInDbCount();
        
        if (failed > 0) {
            return Response.status(503)
                .entity(Map.of(
                    "status", "UNHEALTHY",
                    "pending", pending,
                    "failed", failed
                ))
                .build();
        }
        
        return Response.ok(Map.of(
            "status", "HEALTHY",
            "pending", pending,
            "published", outboxWorker.getPublishedCount()
        )).build();
    }
}
```

### 10.3 Operational Runbook

#### Scenario: High Pending Count

1. Check Kafka connectivity: `curl -k http://kafka:8080/health`
2. Check application logs for publish errors
3. Temporarily increase batch size: `outbox.batch-size=500`
4. Scale out application instances if needed

#### Scenario: Failed Events

1. Query failed events: `SELECT * FROM ar_outbox WHERE status = 'FAILED'`
2. Investigate `last_error` column
3. Fix root cause (e.g., Kafka topic permissions)
4. Reset for retry: `UPDATE ar_outbox SET status='PENDING', retry_count=0 WHERE status='FAILED'`

---

## 11. Future Improvements

### Short Term

| Improvement | Description |
|-------------|-------------|
| **Tenant ID in Kafka headers** | Use `Message<T>` with metadata to set tenant_id header properly |
| **Jackson JSON serialization** | Replace manual JSON building with ObjectMapper |
| **Exponential backoff** | Add delay between retries based on retry_count |
| **Dead letter topic** | Send failed events to a dead-letter topic instead of marking FAILED |

### Long Term

| Improvement | Description |
|-------------|-------------|
| **Change Data Capture (CDC)** | Use Debezium to read from outbox table instead of polling |
| **Schema Registry** | Use Avro/Protobuf with schema registry for event schemas |
| **Event Versioning** | Support multiple event versions for backward compatibility |
| **Exactly-once delivery** | Implement idempotent consumers on subscriber side |

---

## Appendix: Event Payload Format

### InvoiceIssued Event

```json
{
  "eventId": "018f3b7a-1b2c-4d5e-6f7a-8b9c0d1e2f3a",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "eventType": "InvoiceIssued",
  "occurredAt": "2026-03-23T10:30:00Z",
  "invoiceId": "018f3b7a-1b2c-4d5e-6f7a-8b9c0d1e2f3b",
  "customerRef": "CUST-001",
  "total": {
    "amount": 1000.00,
    "currency": "USD"
  },
  "dueDate": "2026-04-30T00:00:00Z"
}
```

### PaymentAllocated Event

```json
{
  "eventId": "018f3b7a-1b2c-4d5e-6f7a-8b9c0d1e2f3b",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "eventType": "PaymentAllocated",
  "occurredAt": "2026-03-23T10:35:00Z",
  "paymentId": "018f3b7a-1b2c-4d5e-6f7a-8b9c0d1e2f3c",
  "invoiceId": "018f3b7a-1b2c-4d5e-6f7a-8b9c0d1e2f3a",
  "amount": {
    "amount": 500.00,
    "currency": "USD"
  }
}
```

---

*Document created: 2026-03-23*  
*Last updated: 2026-03-23*  
*Author: InvoiceGenie Team*
