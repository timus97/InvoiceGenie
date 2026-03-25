package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.event.PaymentAllocated;
import com.invoicegenie.ar.domain.event.PaymentRecorded;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.DomainEvent;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("KafkaEventPublisher")
@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    private KafkaEventPublisher publisher;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher();
        // Use reflection to inject the mock
        try {
            var field = KafkaEventPublisher.class.getDeclaredField("outboxRepository");
            field.setAccessible(true);
            field.set(publisher, outboxRepository);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Publish Event")
    class PublishEvent {

        @Test
        @DisplayName("should save InvoiceIssued event to outbox")
        void shouldSaveInvoiceIssuedToOutbox() {
            InvoiceId invoiceId = InvoiceId.generate();
            InvoiceIssued event = new InvoiceIssued(
                    tenantId, invoiceId, "CUST-001", 
                    Money.of("1000.00", "USD"), Instant.now().plusSeconds(86400));
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            OutboxEntry entry = captor.getValue();
            assertEquals("INVOICE", entry.getAggregateType());
            assertEquals(invoiceId.getValue(), entry.getAggregateId());
            assertEquals("InvoiceIssued", entry.getEventType());
            assertNotNull(entry.getPayload());
            assertTrue(entry.getPayload().contains("invoiceId"));
        }

        @Test
        @DisplayName("should save PaymentAllocated event to outbox")
        void shouldSavePaymentAllocatedToOutbox() {
            PaymentId paymentId = PaymentId.generate();
            InvoiceId invoiceId = InvoiceId.generate();
            PaymentAllocated event = new PaymentAllocated(
                    tenantId, paymentId, invoiceId, Money.of("500.00", "USD"));
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            OutboxEntry entry = captor.getValue();
            assertEquals("PAYMENT", entry.getAggregateType());
            assertEquals(paymentId.getValue(), entry.getAggregateId());
            assertEquals("PaymentAllocated", entry.getEventType());
            assertTrue(entry.getPayload().contains("paymentId"));
            assertTrue(entry.getPayload().contains("invoiceId"));
        }

        @Test
        @DisplayName("should save PaymentRecorded event to outbox")
        void shouldSavePaymentRecordedToOutbox() {
            PaymentId paymentId = PaymentId.generate();
            PaymentRecorded event = new PaymentRecorded(
                    tenantId, paymentId, "CUST-001", 
                    Money.of("500.00", "USD"), Instant.now());
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            OutboxEntry entry = captor.getValue();
            assertEquals("PAYMENT", entry.getAggregateType());
            assertEquals(paymentId.getValue(), entry.getAggregateId());
            assertEquals("PaymentRecorded", entry.getEventType());
            assertTrue(entry.getPayload().contains("customerRef"));
        }

        @Test
        @DisplayName("should not save null event")
        void shouldNotSaveNullEvent() {
            publisher.publish(null);
            
            verify(outboxRepository, never()).save(any(), any());
        }
    }

    @Nested
    @DisplayName("Event Serialization")
    class EventSerialization {

        @Test
        @DisplayName("should serialize InvoiceIssued with all fields")
        void shouldSerializeInvoiceIssuedWithAllFields() {
            InvoiceId invoiceId = InvoiceId.generate();
            InvoiceIssued event = new InvoiceIssued(
                    tenantId, invoiceId, "CUST-001", 
                    Money.of("1000.00", "USD"), Instant.now().plusSeconds(86400));
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            String payload = captor.getValue().getPayload();
            assertTrue(payload.contains("\"invoiceId\":\"" + invoiceId));
            assertTrue(payload.contains("\"customerRef\":\"CUST-001\""));
            assertTrue(payload.contains("\"total\":"));
            assertTrue(payload.contains("\"dueDate\":\""));
        }

        @Test
        @DisplayName("should serialize PaymentAllocated with money amount")
        void shouldSerializePaymentAllocatedWithMoney() {
            PaymentId paymentId = PaymentId.generate();
            InvoiceId invoiceId = InvoiceId.generate();
            PaymentAllocated event = new PaymentAllocated(
                    tenantId, paymentId, invoiceId, Money.of("500.00", "USD"));
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            String payload = captor.getValue().getPayload();
            assertTrue(payload.contains("\"amount\":"));
            assertTrue(payload.contains("\"currency\":\"USD\""));
        }

        @Test
        @DisplayName("should escape special characters in JSON")
        void shouldEscapeSpecialCharacters() {
            InvoiceId invoiceId = InvoiceId.generate();
            // Customer ref with special characters
            InvoiceIssued event = new InvoiceIssued(
                    tenantId, invoiceId, "CUST\"WITH\"QUOTES\nNEWLINE", 
                    Money.of("100.00", "USD"), Instant.now().plusSeconds(86400));
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            String payload = captor.getValue().getPayload();
            // Verify quotes are escaped
            assertTrue(payload.contains("\\\""));
            // Verify newlines are escaped
            assertTrue(payload.contains("\\n"));
        }
    }

    @Nested
    @DisplayName("Aggregate Type Detection")
    class AggregateTypeDetection {

        @Test
        @DisplayName("should return INVOICE for InvoiceIssued")
        void shouldReturnInvoiceForInvoiceIssued() {
            InvoiceId invoiceId = InvoiceId.generate();
            InvoiceIssued event = new InvoiceIssued(
                    tenantId, invoiceId, "CUST-001", 
                    Money.of("100.00", "USD"), Instant.now());
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            assertEquals("INVOICE", captor.getValue().getAggregateType());
        }

        @Test
        @DisplayName("should return PAYMENT for PaymentAllocated")
        void shouldReturnPaymentForPaymentAllocated() {
            PaymentId paymentId = PaymentId.generate();
            PaymentAllocated event = new PaymentAllocated(
                    tenantId, paymentId, InvoiceId.generate(), Money.of("100.00", "USD"));
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            assertEquals("PAYMENT", captor.getValue().getAggregateType());
        }

        @Test
        @DisplayName("should return PAYMENT for PaymentRecorded")
        void shouldReturnPaymentForPaymentRecorded() {
            PaymentId paymentId = PaymentId.generate();
            PaymentRecorded event = new PaymentRecorded(
                    tenantId, paymentId, "CUST-001", 
                    Money.of("100.00", "USD"), Instant.now());
            
            publisher.publish(event);
            
            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());
            
            assertEquals("PAYMENT", captor.getValue().getAggregateType());
        }
    }
}
