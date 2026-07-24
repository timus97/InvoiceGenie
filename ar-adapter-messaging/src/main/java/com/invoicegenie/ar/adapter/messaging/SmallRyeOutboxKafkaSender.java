package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * OutboxKafkaSender bean (P1-03).
 *
 * <p>When {@code outbox.kafka-enabled=true}, emits payloads to the configured
 * Kafka topic using the Apache Kafka producer client. When disabled, no-ops
 * (OutboxWorker still marks entries published after successful {@link #send}).
 *
 * <p>Uses a lazy producer so local/dev/test without Kafka still start cleanly.
 */
@ApplicationScoped
public class SmallRyeOutboxKafkaSender implements OutboxKafkaSender {

    private static final Logger LOG = Logger.getLogger(SmallRyeOutboxKafkaSender.class);

    @ConfigProperty(name = "outbox.kafka-enabled", defaultValue = "false")
    boolean kafkaEnabled;

    @ConfigProperty(name = "outbox.kafka.bootstrap-servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    @ConfigProperty(name = "outbox.kafka.topic", defaultValue = "ar.domain.events")
    String topic;

    @ConfigProperty(name = "outbox.kafka.client-id", defaultValue = "invoicegenie-outbox")
    String clientId;

    private volatile org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @Override
    public void send(OutboxEntry entry) {
        if (!kafkaEnabled) {
            LOG.debugf("Kafka emit skipped (outbox.kafka-enabled=false) id=%s type=%s",
                    entry.getId(), entry.getEventType());
            return;
        }

        String key = entry.getTenantId() != null
                ? entry.getTenantId().getValue().toString()
                : entry.getId().toString();
        String payload = entry.getPayload() != null ? entry.getPayload() : "{}";

        try {
            org.apache.kafka.clients.producer.KafkaProducer<String, String> p = producer();
            var record = new org.apache.kafka.clients.producer.ProducerRecord<>(topic, key, payload);
            record.headers().add("eventType", entry.getEventType().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            record.headers().add("aggregateType",
                    (entry.getAggregateType() != null ? entry.getAggregateType() : "UNKNOWN")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            p.send(record).get(10, TimeUnit.SECONDS);
            LOG.infof("Published outbox event to Kafka topic=%s type=%s id=%s tenant=%s",
                    topic, entry.getEventType(), entry.getId(), key);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish outbox entry " + entry.getId() + " to Kafka", e);
        }
    }

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer() {
        org.apache.kafka.clients.producer.KafkaProducer<String, String> local = producer;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (producer == null) {
                Properties props = new Properties();
                props.put("bootstrap.servers", bootstrapServers);
                props.put("client.id", clientId);
                props.put("acks", "all");
                props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                props.put("linger.ms", "5");
                producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
                LOG.infof("Created Kafka producer bootstrap=%s topic=%s", bootstrapServers, topic);
            }
            return producer;
        }
    }

    @PreDestroy
    void shutdown() {
        org.apache.kafka.clients.producer.KafkaProducer<String, String> local = producer;
        if (local != null) {
            try {
                local.close(java.time.Duration.ofSeconds(5));
            } catch (Exception e) {
                LOG.debugf(e, "Error closing Kafka producer");
            }
        }
    }
}