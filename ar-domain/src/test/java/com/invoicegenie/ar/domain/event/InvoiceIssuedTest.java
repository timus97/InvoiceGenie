package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InvoiceIssued Event")
class InvoiceIssuedTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create event with all fields")
        void shouldCreateWithAllFields() {
            UUID eventId = UUID.randomUUID();
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            InvoiceId invoiceId = InvoiceId.of(UUID.randomUUID());
            Money total = Money.of("1000.00", "USD");
            Instant dueDate = Instant.now().plusSeconds(86400 * 30);
            Instant occurredAt = Instant.now();
            
            InvoiceIssued event = new InvoiceIssued(
                    eventId, tenantId, invoiceId, "CUST-001", total, dueDate, occurredAt);
            
            assertEquals(eventId, event.eventId());
            assertEquals(tenantId, event.tenantId());
            assertEquals(invoiceId, event.invoiceId());
            assertEquals("CUST-001", event.customerRef());
            assertEquals(total, event.total());
            assertEquals(dueDate, event.dueDate());
            assertEquals(occurredAt, event.occurredAt());
        }

        @Test
        @DisplayName("should generate eventId if null")
        void shouldGenerateEventIdIfNull() {
            InvoiceIssued event = new InvoiceIssued(
                    null, TenantId.of(UUID.randomUUID()), InvoiceId.of(UUID.randomUUID()),
                    "CUST-001", Money.of("100.00", "USD"), Instant.now(), Instant.now());
            
            assertNotNull(event.eventId());
        }

        @Test
        @DisplayName("should set occurredAt if null")
        void shouldSetOccurredAtIfNull() {
            InvoiceIssued event = new InvoiceIssued(
                    UUID.randomUUID(), TenantId.of(UUID.randomUUID()), InvoiceId.of(UUID.randomUUID()),
                    "CUST-001", Money.of("100.00", "USD"), Instant.now(), null);
            
            assertNotNull(event.occurredAt());
        }

        @Test
        @DisplayName("should create with convenience constructor")
        void shouldCreateWithConvenienceConstructor() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            InvoiceId invoiceId = InvoiceId.of(UUID.randomUUID());
            Money total = Money.of("1000.00", "USD");
            Instant dueDate = Instant.now().plusSeconds(86400 * 30);
            
            InvoiceIssued event = new InvoiceIssued(tenantId, invoiceId, "CUST-001", total, dueDate);
            
            assertNotNull(event.eventId());
            assertEquals(tenantId, event.tenantId());
            assertEquals(invoiceId, event.invoiceId());
            assertEquals("CUST-001", event.customerRef());
            assertEquals(total, event.total());
            assertEquals(dueDate, event.dueDate());
            assertNotNull(event.occurredAt());
        }
    }

    @Nested
    @DisplayName("DomainEvent Interface")
    class DomainEventInterface {

        @Test
        @DisplayName("should implement DomainEvent")
        void shouldImplementDomainEvent() {
            InvoiceIssued event = new InvoiceIssued(
                    TenantId.of(UUID.randomUUID()), InvoiceId.of(UUID.randomUUID()),
                    "CUST-001", Money.of("100.00", "USD"), Instant.now());
            
            assertTrue(event instanceof com.invoicegenie.shared.domain.DomainEvent);
        }
    }
}
