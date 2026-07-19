package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.event.PaymentAllocated;
import com.invoicegenie.ar.domain.event.PaymentRecorded;
import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.shared.domain.DomainEvent;
import com.invoicegenie.shared.domain.Money;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.jboss.logging.Logger;

/**
 * Driven adapter: publishes domain events using the transactional outbox pattern.
 *
 * <p>Instead of publishing directly to Kafka, this implementation saves events
 * to the outbox table in the same transaction as the aggregate changes.
 * The OutboxWorker then polls the table and publishes to Kafka asynchronously.
 *
 * <p>Serialization uses Jackson {@link ObjectMapper}. Aggregate type / id
 * resolution uses an event-type registry (no long instanceof chains).
 */
@ApplicationScoped
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisher.class);

    /** Registry: event class → aggregate metadata + payload enricher. */
    private static final Map<Class<? extends DomainEvent>, EventTypeInfo> EVENT_REGISTRY = Map.of(
            InvoiceIssued.class, new EventTypeInfo(
                    "INVOICE",
                    e -> ((InvoiceIssued) e).invoiceId().getValue(),
                    KafkaEventPublisher::invoiceIssuedFields),
            PaymentAllocated.class, new EventTypeInfo(
                    "PAYMENT",
                    e -> ((PaymentAllocated) e).paymentId().getValue(),
                    KafkaEventPublisher::paymentAllocatedFields),
            PaymentRecorded.class, new EventTypeInfo(
                    "PAYMENT",
                    e -> ((PaymentRecorded) e).paymentId().getValue(),
                    KafkaEventPublisher::paymentRecordedFields)
    );

    @Inject
    OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper;

    public KafkaEventPublisher() {
        this.objectMapper = createObjectMapper();
    }

    /** Package-private for unit tests that need a known ObjectMapper. */
    KafkaEventPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        if (event == null) {
            LOG.warn("Attempted to publish null event, ignoring");
            return;
        }

        OutboxEntry entry = createOutboxEntry(event);
        outboxRepository.save(event.tenantId(), entry);

        LOG.debugf("Saved event to outbox: type=%s, id=%s, aggregateId=%s",
                entry.getEventType(), entry.getId(), entry.getAggregateId());
    }

    private OutboxEntry createOutboxEntry(DomainEvent event) {
        EventTypeInfo info = EVENT_REGISTRY.get(event.getClass());
        String aggregateType = info != null ? info.aggregateType() : "UNKNOWN";
        UUID aggregateId = info != null ? info.aggregateIdExtractor().apply(event) : event.eventId();
        String eventType = event.getClass().getSimpleName();
        String payload = serializeEvent(event, info);

        return new OutboxEntry(
                event.tenantId(),
                aggregateType,
                aggregateId,
                eventType,
                payload
        );
    }

    /**
     * Serializes the event to JSON via Jackson for storage in the outbox.
     */
    private String serializeEvent(DomainEvent event, EventTypeInfo info) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId().toString());
        payload.put("tenantId", event.tenantId().toString());
        payload.put("eventType", event.getClass().getSimpleName());
        payload.put("occurredAt", event.occurredAt().toString());

        if (info != null) {
            payload.putAll(info.fieldExtractor().apply(event));
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialize event %s, falling back to minimal payload",
                    event.getClass().getSimpleName());
            return "{\"eventId\":\"" + event.eventId() + "\",\"eventType\":\""
                    + event.getClass().getSimpleName() + "\",\"error\":\"serialization_failed\"}";
        }
    }

    private static Map<String, Object> invoiceIssuedFields(DomainEvent event) {
        InvoiceIssued ie = (InvoiceIssued) event;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("invoiceId", ie.invoiceId().toString());
        fields.put("customerRef", ie.customerRef());
        fields.put("total", moneyMap(ie.total()));
        fields.put("dueDate", ie.dueDate() != null ? ie.dueDate().toString() : null);
        return fields;
    }

    private static Map<String, Object> paymentAllocatedFields(DomainEvent event) {
        PaymentAllocated pa = (PaymentAllocated) event;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("paymentId", pa.paymentId().toString());
        fields.put("invoiceId", pa.invoiceId().toString());
        fields.put("amount", moneyMap(pa.amount()));
        return fields;
    }

    private static Map<String, Object> paymentRecordedFields(DomainEvent event) {
        PaymentRecorded pr = (PaymentRecorded) event;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("paymentId", pr.paymentId().toString());
        fields.put("customerRef", pr.customerRef());
        fields.put("amount", moneyMap(pr.amount()));
        fields.put("paymentDate", pr.paymentDate() != null ? pr.paymentDate().toString() : null);
        return fields;
    }

    private static Map<String, Object> moneyMap(Money money) {
        Map<String, Object> m = new LinkedHashMap<>();
        // Plain string preserves scale (e.g. 1000.00) and avoids JSON number float noise
        m.put("amount", money.getAmount().toPlainString());
        m.put("currency", money.getCurrencyCode());
        return m;
    }

    /**
     * Event type metadata used by the registry.
     */
    record EventTypeInfo(
            String aggregateType,
            Function<DomainEvent, UUID> aggregateIdExtractor,
            Function<DomainEvent, Map<String, Object>> fieldExtractor
    ) {}
}
