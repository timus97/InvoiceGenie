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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        publisher = new KafkaEventPublisher(objectMapper);
        try {
            var field = KafkaEventPublisher.class.getDeclaredField("outboxRepository");
            field.setAccessible(true);
            field.set(publisher, outboxRepository);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        tenantId = TenantId.of(UUID.randomUUID());
    }

    private JsonNode parsePayload(OutboxEntry entry) throws Exception {
        return objectMapper.readTree(entry.getPayload());
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
    @DisplayName("Event Serialization (Jackson)")
    class EventSerialization {

        @Test
        @DisplayName("should serialize InvoiceIssued with all fields as valid JSON")
        void shouldSerializeInvoiceIssuedWithAllFields() throws Exception {
            InvoiceId invoiceId = InvoiceId.generate();
            Instant due = Instant.now().plusSeconds(86400);
            InvoiceIssued event = new InvoiceIssued(
                    tenantId, invoiceId, "CUST-001",
                    Money.of("1000.00", "USD"), due);

            publisher.publish(event);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            JsonNode payload = parsePayload(captor.getValue());
            assertEquals(event.eventId().toString(), payload.get("eventId").asText());
            assertEquals(tenantId.toString(), payload.get("tenantId").asText());
            assertEquals("InvoiceIssued", payload.get("eventType").asText());
            assertEquals(invoiceId.toString(), payload.get("invoiceId").asText());
            assertEquals("CUST-001", payload.get("customerRef").asText());
            assertEquals("1000.00", payload.get("total").get("amount").asText());
            assertEquals("USD", payload.get("total").get("currency").asText());
            assertNotNull(payload.get("dueDate").asText());
            assertNotNull(payload.get("occurredAt").asText());
        }

        @Test
        @DisplayName("should serialize PaymentAllocated with money amount")
        void shouldSerializePaymentAllocatedWithMoney() throws Exception {
            PaymentId paymentId = PaymentId.generate();
            InvoiceId invoiceId = InvoiceId.generate();
            PaymentAllocated event = new PaymentAllocated(
                    tenantId, paymentId, invoiceId, Money.of("500.00", "USD"));

            publisher.publish(event);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            JsonNode payload = parsePayload(captor.getValue());
            assertEquals(paymentId.toString(), payload.get("paymentId").asText());
            assertEquals(invoiceId.toString(), payload.get("invoiceId").asText());
            assertEquals("500.00", payload.get("amount").get("amount").asText());
            assertEquals("USD", payload.get("amount").get("currency").asText());
        }

        @Test
        @DisplayName("should serialize PaymentRecorded with all fields")
        void shouldSerializePaymentRecordedWithAllFields() throws Exception {
            PaymentId paymentId = PaymentId.generate();
            Instant paymentDate = Instant.parse("2026-03-15T10:00:00Z");
            PaymentRecorded event = new PaymentRecorded(
                    tenantId, paymentId, "CUST-042",
                    Money.of("250.50", "EUR"), paymentDate);

            publisher.publish(event);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            JsonNode payload = parsePayload(captor.getValue());
            assertEquals("PaymentRecorded", payload.get("eventType").asText());
            assertEquals("CUST-042", payload.get("customerRef").asText());
            assertEquals("250.50", payload.get("amount").get("amount").asText());
            assertEquals("EUR", payload.get("amount").get("currency").asText());
            assertTrue(payload.get("paymentDate").asText().contains("2026-03-15"));
        }

        @Test
        @DisplayName("should escape special characters via Jackson")
        void shouldEscapeSpecialCharacters() throws Exception {
            InvoiceId invoiceId = InvoiceId.generate();
            InvoiceIssued event = new InvoiceIssued(
                    tenantId, invoiceId, "CUST\"WITH\"QUOTES\nNEWLINE",
                    Money.of("100.00", "USD"), Instant.now().plusSeconds(86400));

            publisher.publish(event);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            // Must be valid JSON that round-trips special characters
            JsonNode payload = parsePayload(captor.getValue());
            assertEquals("CUST\"WITH\"QUOTES\nNEWLINE", payload.get("customerRef").asText());
            // Raw string should contain JSON escapes
            String raw = captor.getValue().getPayload();
            assertTrue(raw.contains("\\\"") || raw.contains("\\n"));
        }

        @Test
        @DisplayName("payload must be parseable JSON object")
        void payloadMustBeValidJsonObject() throws Exception {
            InvoiceIssued event = new InvoiceIssued(
                    tenantId, InvoiceId.generate(), "C1",
                    Money.of("1.00", "USD"), Instant.now());
            publisher.publish(event);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            JsonNode node = objectMapper.readTree(captor.getValue().getPayload());
            assertTrue(node.isObject());
        }
    }

    @Nested
    @DisplayName("Aggregate Type Detection (registry)")
    class AggregateTypeDetection {

        @Test
        @DisplayName("should return INVOICE for InvoiceIssued")
        void shouldReturnInvoiceForInvoiceIssued() {
            InvoiceIssued event = new InvoiceIssued(
                    tenantId, InvoiceId.generate(), "CUST-001",
                    Money.of("100.00", "USD"), Instant.now());

            publisher.publish(event);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            assertEquals("INVOICE", captor.getValue().getAggregateType());
        }

        @Test
        @DisplayName("should return PAYMENT for PaymentAllocated")
        void shouldReturnPaymentForPaymentAllocated() {
            PaymentAllocated event = new PaymentAllocated(
                    tenantId, PaymentId.generate(), InvoiceId.generate(), Money.of("100.00", "USD"));

            publisher.publish(event);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            assertEquals("PAYMENT", captor.getValue().getAggregateType());
        }

        @Test
        @DisplayName("should return PAYMENT for PaymentRecorded")
        void shouldReturnPaymentForPaymentRecorded() {
            PaymentRecorded event = new PaymentRecorded(
                    tenantId, PaymentId.generate(), "CUST-001",
                    Money.of("100.00", "USD"), Instant.now());

            publisher.publish(event);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            assertEquals("PAYMENT", captor.getValue().getAggregateType());
        }

        @Test
        @DisplayName("should return UNKNOWN for unregistered event type")
        void shouldReturnUnknownForUnregisteredEvent() {
            DomainEvent unknown = new DomainEvent() {
                private final UUID id = UUID.randomUUID();
                @Override public UUID eventId() { return id; }
                @Override public TenantId tenantId() { return tenantId; }
                @Override public Instant occurredAt() { return Instant.now(); }
            };

            publisher.publish(unknown);

            ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
            verify(outboxRepository).save(eq(tenantId), captor.capture());

            assertEquals("UNKNOWN", captor.getValue().getAggregateType());
            assertEquals(unknown.eventId(), captor.getValue().getAggregateId());
        }
    }
}