package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentAllocated Event")
class PaymentAllocatedTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create event with all fields")
        void shouldCreateWithAllFields() {
            UUID eventId = UUID.randomUUID();
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            PaymentId paymentId = PaymentId.of(UUID.randomUUID());
            InvoiceId invoiceId = InvoiceId.of(UUID.randomUUID());
            Money amount = Money.of("500.00", "USD");
            Instant occurredAt = Instant.now();
            
            PaymentAllocated event = new PaymentAllocated(
                    eventId, tenantId, paymentId, invoiceId, amount, occurredAt);
            
            assertEquals(eventId, event.eventId());
            assertEquals(tenantId, event.tenantId());
            assertEquals(paymentId, event.paymentId());
            assertEquals(invoiceId, event.invoiceId());
            assertEquals(amount, event.amount());
            assertEquals(occurredAt, event.occurredAt());
        }

        @Test
        @DisplayName("should generate eventId if null")
        void shouldGenerateEventIdIfNull() {
            PaymentAllocated event = new PaymentAllocated(
                    null, TenantId.of(UUID.randomUUID()), PaymentId.of(UUID.randomUUID()),
                    InvoiceId.of(UUID.randomUUID()), Money.of("100.00", "USD"), Instant.now());
            
            assertNotNull(event.eventId());
        }

        @Test
        @DisplayName("should set occurredAt if null")
        void shouldSetOccurredAtIfNull() {
            PaymentAllocated event = new PaymentAllocated(
                    UUID.randomUUID(), TenantId.of(UUID.randomUUID()), PaymentId.of(UUID.randomUUID()),
                    InvoiceId.of(UUID.randomUUID()), Money.of("100.00", "USD"), null);
            
            assertNotNull(event.occurredAt());
        }

        @Test
        @DisplayName("should create with convenience constructor")
        void shouldCreateWithConvenienceConstructor() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            PaymentId paymentId = PaymentId.of(UUID.randomUUID());
            InvoiceId invoiceId = InvoiceId.of(UUID.randomUUID());
            Money amount = Money.of("500.00", "USD");
            
            PaymentAllocated event = new PaymentAllocated(tenantId, paymentId, invoiceId, amount);
            
            assertNotNull(event.eventId());
            assertEquals(tenantId, event.tenantId());
            assertEquals(paymentId, event.paymentId());
            assertEquals(invoiceId, event.invoiceId());
            assertEquals(amount, event.amount());
            assertNotNull(event.occurredAt());
        }
    }

    @Nested
    @DisplayName("DomainEvent Interface")
    class DomainEventInterface {

        @Test
        @DisplayName("should implement DomainEvent")
        void shouldImplementDomainEvent() {
            PaymentAllocated event = new PaymentAllocated(
                    TenantId.of(UUID.randomUUID()), PaymentId.of(UUID.randomUUID()),
                    InvoiceId.of(UUID.randomUUID()), Money.of("100.00", "USD"));
            
            assertTrue(event instanceof com.invoicegenie.shared.domain.DomainEvent);
        }
    }
}
