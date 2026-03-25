package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentRecorded Event")
class PaymentRecordedTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create event with all fields")
        void shouldCreateWithAllFields() {
            UUID eventId = UUID.randomUUID();
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            PaymentId paymentId = PaymentId.of(UUID.randomUUID());
            Money amount = Money.of("500.00", "USD");
            Instant paymentDate = Instant.now();
            Instant occurredAt = Instant.now();
            
            PaymentRecorded event = new PaymentRecorded(
                    eventId, tenantId, paymentId, "CUST-001", amount, paymentDate, occurredAt);
            
            assertEquals(eventId, event.eventId());
            assertEquals(tenantId, event.tenantId());
            assertEquals(paymentId, event.paymentId());
            assertEquals("CUST-001", event.customerRef());
            assertEquals(amount, event.amount());
            assertEquals(paymentDate, event.paymentDate());
            assertEquals(occurredAt, event.occurredAt());
        }

        @Test
        @DisplayName("should generate eventId if null")
        void shouldGenerateEventIdIfNull() {
            PaymentRecorded event = new PaymentRecorded(
                    null, TenantId.of(UUID.randomUUID()), PaymentId.of(UUID.randomUUID()),
                    "CUST-001", Money.of("100.00", "USD"), Instant.now(), Instant.now());
            
            assertNotNull(event.eventId());
        }

        @Test
        @DisplayName("should set occurredAt if null")
        void shouldSetOccurredAtIfNull() {
            PaymentRecorded event = new PaymentRecorded(
                    UUID.randomUUID(), TenantId.of(UUID.randomUUID()), PaymentId.of(UUID.randomUUID()),
                    "CUST-001", Money.of("100.00", "USD"), Instant.now(), null);
            
            assertNotNull(event.occurredAt());
        }

        @Test
        @DisplayName("should create with convenience constructor")
        void shouldCreateWithConvenienceConstructor() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            PaymentId paymentId = PaymentId.of(UUID.randomUUID());
            Money amount = Money.of("500.00", "USD");
            Instant paymentDate = Instant.now();
            
            PaymentRecorded event = new PaymentRecorded(tenantId, paymentId, "CUST-001", amount, paymentDate);
            
            assertNotNull(event.eventId());
            assertEquals(tenantId, event.tenantId());
            assertEquals(paymentId, event.paymentId());
            assertEquals("CUST-001", event.customerRef());
            assertEquals(amount, event.amount());
            assertEquals(paymentDate, event.paymentDate());
            assertNotNull(event.occurredAt());
        }
    }

    @Nested
    @DisplayName("DomainEvent Interface")
    class DomainEventInterface {

        @Test
        @DisplayName("should implement DomainEvent")
        void shouldImplementDomainEvent() {
            PaymentRecorded event = new PaymentRecorded(
                    TenantId.of(UUID.randomUUID()), PaymentId.of(UUID.randomUUID()),
                    "CUST-001", Money.of("100.00", "USD"), Instant.now());
            
            assertTrue(event instanceof com.invoicegenie.shared.domain.DomainEvent);
        }
    }
}
