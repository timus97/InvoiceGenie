package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.event.PaymentAllocated;
import com.invoicegenie.ar.domain.event.PaymentRecorded;
import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.shared.domain.DomainEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

import org.jboss.logging.Logger;

/**
 * Driven adapter: publishes domain events using the transactional outbox pattern.
 * 
 * <p>Instead of publishing directly to Kafka, this implementation saves events
 * to the outbox table in the same transaction as the aggregate changes.
 * The OutboxWorker then polls the table and publishes to Kafka asynchronously.
 * 
 * <p>This ensures:
 * <ul>
 *   <li>Events are never lost (same transaction as aggregate)</li>
 *   <li>Ordering is preserved (FIFO by creation time)</li>
 *   <li>Tenant isolation (tenant_id in every event)</li>
 *   <li>Automatic retry on failure</li>
 * </ul>
 */
@ApplicationScoped
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisher.class);

    @Inject
    OutboxRepository outboxRepository;

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

    /**
     * Creates an outbox entry from a domain event.
     */
    private OutboxEntry createOutboxEntry(DomainEvent event) {
        String aggregateType = getAggregateType(event);
        UUID aggregateId = getAggregateId(event);
        String eventType = event.getClass().getSimpleName();
        String payload = serializeEvent(event);

        return new OutboxEntry(
                event.tenantId(),
                aggregateType,
                aggregateId,
                eventType,
                payload
        );
    }

    /**
     * Determines the aggregate type from the event class.
     */
    private String getAggregateType(DomainEvent event) {
        if (event instanceof InvoiceIssued) {
            return "INVOICE";
        } else if (event instanceof PaymentAllocated) {
            return "PAYMENT";
        } else if (event instanceof PaymentRecorded) {
            return "PAYMENT";
        }
        return "UNKNOWN";
    }

    /**
     * Extracts the aggregate ID from the event.
     */
    private UUID getAggregateId(DomainEvent event) {
        if (event instanceof InvoiceIssued ie) {
            return ie.invoiceId().getValue();
        } else if (event instanceof PaymentAllocated pa) {
            return pa.paymentId().getValue();
        } else if (event instanceof PaymentRecorded pr) {
            return pr.paymentId().getValue();
        }
        return event.eventId(); // Fallback to event ID
    }

    /**
     * Serializes the event to JSON for storage in the outbox.
     * 
     * <p>Uses a simple JSON format. In production, consider using
     * a proper JSON library like Jackson with a schema registry.
     */
    private String serializeEvent(DomainEvent event) {
        // Simple JSON serialization
        // In production, use Jackson ObjectMapper or similar
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"eventId\":\"").append(event.eventId()).append("\",");
        sb.append("\"tenantId\":\"").append(event.tenantId()).append("\",");
        sb.append("\"eventType\":\"").append(event.getClass().getSimpleName()).append("\",");
        sb.append("\"occurredAt\":\"").append(event.occurredAt()).append("\"");
        
        // Add event-specific fields
        if (event instanceof InvoiceIssued ie) {
            sb.append(",\"invoiceId\":\"").append(ie.invoiceId()).append("\"");
            sb.append(",\"customerRef\":\"").append(escapeJson(ie.customerRef())).append("\"");
            sb.append(",\"total\":").append(serializeMoney(ie.total()));
            sb.append(",\"dueDate\":\"").append(ie.dueDate()).append("\"");
        } else if (event instanceof PaymentAllocated pa) {
            sb.append(",\"paymentId\":\"").append(pa.paymentId()).append("\"");
            sb.append(",\"invoiceId\":\"").append(pa.invoiceId()).append("\"");
            sb.append(",\"amount\":").append(serializeMoney(pa.amount()));
        } else if (event instanceof PaymentRecorded pr) {
            sb.append(",\"paymentId\":\"").append(pr.paymentId()).append("\"");
            sb.append(",\"customerRef\":\"").append(escapeJson(pr.customerRef())).append("\"");
            sb.append(",\"amount\":").append(serializeMoney(pr.amount()));
            sb.append(",\"paymentDate\":\"").append(pr.paymentDate()).append("\"");
        }
        
        sb.append("}");
        return sb.toString();
    }

    /**
     * Serializes a Money value object to JSON.
     */
    private String serializeMoney(com.invoicegenie.shared.domain.Money money) {
        return String.format("{\"amount\":%s,\"currency\":\"%s\"}",
                money.getAmount().toPlainString(),
                money.getCurrencyCode());
    }

    /**
     * Escapes special characters for JSON strings.
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

