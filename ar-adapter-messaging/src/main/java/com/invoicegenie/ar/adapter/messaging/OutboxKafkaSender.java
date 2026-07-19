package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;

/**
 * Optional Kafka sink for outbox entries.
 *
 * <p>When {@code outbox.kafka-enabled=true} and a CDI bean implementing this
 * interface is present, {@link OutboxWorker} uses it to emit messages.
 * Otherwise events are logged only (safe default for local/dev without Kafka).
 *
 * <p>Production wiring example:
 * <pre>
 * {@literal @}ApplicationScoped
 * public class SmallRyeOutboxKafkaSender implements OutboxKafkaSender {
 *     {@literal @}Inject {@literal @}Channel("outbox-events") Emitter&lt;String&gt; emitter;
 *     public void send(OutboxEntry entry) {
 *         emitter.send(entry.getPayload());
 *     }
 * }
 * </pre>
 */
public interface OutboxKafkaSender {

    /**
     * Emit a single outbox entry to Kafka (or another message broker).
     *
     * @param entry the outbox entry to publish
     */
    void send(OutboxEntry entry);
}
