package com.invoicegenie.ar.domain.model.outbox;

import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxEntry")
class OutboxEntryTest {

    private TenantId tenantId;
    private UUID aggregateId;
    private OutboxEntry entry;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.of(UUID.randomUUID());
        aggregateId = UUID.randomUUID();
        entry = new OutboxEntry(tenantId, "INVOICE", aggregateId, 
                "InvoiceIssued", "{\"invoiceId\":\"" + aggregateId + "\"}");
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create entry with required fields")
        void shouldCreateWithRequiredFields() {
            assertNotNull(entry.getId());
            assertEquals(tenantId, entry.getTenantId());
            assertEquals("INVOICE", entry.getAggregateType());
            assertEquals(aggregateId, entry.getAggregateId());
            assertEquals("InvoiceIssued", entry.getEventType());
            assertEquals(OutboxStatus.PENDING, entry.getStatus());
            assertEquals(0, entry.getRetryCount());
        }

        @Test
        @DisplayName("should create entry with all fields for reconstitution")
        void shouldCreateWithAllFields() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            OutboxEntry fullEntry = new OutboxEntry(
                    id, tenantId, "PAYMENT", aggregateId,
                    "PaymentRecorded", "{}", now, now,
                    OutboxStatus.PUBLISHED, 2, "Previous error"
            );
            
            assertEquals(id, fullEntry.getId());
            assertEquals("PAYMENT", fullEntry.getAggregateType());
            assertEquals(OutboxStatus.PUBLISHED, fullEntry.getStatus());
            assertEquals(2, fullEntry.getRetryCount());
            assertEquals("Previous error", fullEntry.getLastError());
        }

        @Test
        @DisplayName("should throw when tenantId is null")
        void shouldThrowWhenTenantIdNull() {
            assertThrows(NullPointerException.class, () ->
                    new OutboxEntry(null, "INVOICE", aggregateId, "Event", "{}"));
        }

        @Test
        @DisplayName("should throw when aggregateType is null")
        void shouldThrowWhenAggregateTypeNull() {
            assertThrows(NullPointerException.class, () ->
                    new OutboxEntry(tenantId, null, aggregateId, "Event", "{}"));
        }

        @Test
        @DisplayName("should throw when aggregateId is null")
        void shouldThrowWhenAggregateIdNull() {
            assertThrows(NullPointerException.class, () ->
                    new OutboxEntry(tenantId, "INVOICE", null, "Event", "{}"));
        }

        @Test
        @DisplayName("should throw when eventType is null")
        void shouldThrowWhenEventTypeNull() {
            assertThrows(NullPointerException.class, () ->
                    new OutboxEntry(tenantId, "INVOICE", aggregateId, null, "{}"));
        }

        @Test
        @DisplayName("should throw when payload is null")
        void shouldThrowWhenPayloadNull() {
            assertThrows(NullPointerException.class, () ->
                    new OutboxEntry(tenantId, "INVOICE", aggregateId, "Event", null));
        }
    }

    @Nested
    @DisplayName("Status Transitions")
    class StatusTransitions {

        @Test
        @DisplayName("should mark as processing")
        void shouldMarkAsProcessing() {
            OutboxEntry result = entry.markProcessing();
            
            assertEquals(OutboxStatus.PROCESSING, entry.getStatus());
            assertSame(entry, result); // Fluent API
        }

        @Test
        @DisplayName("should mark as published")
        void shouldMarkAsPublished() {
            entry.markProcessing();
            OutboxEntry result = entry.markPublished();
            
            assertEquals(OutboxStatus.PUBLISHED, entry.getStatus());
            assertNotNull(entry.getPublishedAt());
            assertSame(entry, result);
        }

        @Test
        @DisplayName("should mark as failed and increment retry count")
        void shouldMarkAsFailed() {
            entry.markProcessing();
            entry.markFailed("Connection refused");
            
            assertEquals(1, entry.getRetryCount());
            assertEquals("Connection refused", entry.getLastError());
            assertEquals(OutboxStatus.PENDING, entry.getStatus()); // Still pending for retry
        }

        @Test
        @DisplayName("should mark as FAILED after max retries")
        void shouldMarkAsFailedAfterMaxRetries() {
            entry.markProcessing();
            for (int i = 0; i < OutboxEntry.getMaxRetries(); i++) {
                entry.markFailed("Error " + i);
            }
            
            assertEquals(OutboxStatus.FAILED, entry.getStatus());
            assertFalse(entry.canRetry());
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryLogic {

        @Test
        @DisplayName("should allow retry when under max retries")
        void shouldAllowRetryWhenUnderMax() {
            assertTrue(entry.canRetry());
            
            for (int i = 0; i < OutboxEntry.getMaxRetries() - 1; i++) {
                entry.markFailed("Error");
                assertTrue(entry.canRetry());
            }
        }

        @Test
        @DisplayName("should not allow retry after max retries")
        void shouldNotAllowRetryAfterMax() {
            for (int i = 0; i < OutboxEntry.getMaxRetries(); i++) {
                entry.markFailed("Error");
            }
            assertFalse(entry.canRetry());
        }

        @Test
        @DisplayName("max retries should be 5")
        void maxRetriesShouldBe5() {
            assertEquals(5, OutboxEntry.getMaxRetries());
        }
    }

    @Nested
    @DisplayName("Topic Name")
    class TopicName {

        @Test
        @DisplayName("should generate topic name for InvoiceIssued")
        void shouldGenerateTopicNameForInvoiceIssued() {
            assertEquals("ar.invoice.invoice-issued", entry.getTopicName());
        }

        @Test
        @DisplayName("should generate topic name for PaymentRecorded")
        void shouldGenerateTopicNameForPaymentRecorded() {
            OutboxEntry paymentEntry = new OutboxEntry(tenantId, "PAYMENT", aggregateId,
                    "PaymentRecorded", "{}");
            assertEquals("ar.payment.payment-recorded", paymentEntry.getTopicName());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal by id")
        void shouldBeEqualById() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            
            OutboxEntry e1 = new OutboxEntry(id, tenantId, "INVOICE", aggregateId,
                    "Event", "{}", now, null, OutboxStatus.PENDING, 0, null);
            OutboxEntry e2 = new OutboxEntry(id, TenantId.of(UUID.randomUUID()), "PAYMENT",
                    UUID.randomUUID(), "Other", "[]", now, now, OutboxStatus.PUBLISHED, 5, "Error");
            
            assertEquals(e1, e2);
        }

        @Test
        @DisplayName("should not be equal when different ids")
        void shouldNotBeEqualWhenDifferentIds() {
            OutboxEntry other = new OutboxEntry(tenantId, "INVOICE", aggregateId,
                    "InvoiceIssued", "{}");
            assertNotEquals(entry, other);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("should contain key fields")
        void shouldContainKeyFields() {
            String str = entry.toString();
            
            assertTrue(str.contains("id="));
            assertTrue(str.contains("aggregateType='INVOICE'"));
            assertTrue(str.contains("eventType='InvoiceIssued'"));
            assertTrue(str.contains("status=PENDING"));
        }
    }
}
