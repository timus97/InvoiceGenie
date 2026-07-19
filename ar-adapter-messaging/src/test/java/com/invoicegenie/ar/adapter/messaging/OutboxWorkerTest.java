package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.ar.domain.model.outbox.OutboxStatus;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@DisplayName("OutboxWorker")
@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private Instance<OutboxKafkaSender> kafkaSenderInstance;

    @Mock
    private OutboxKafkaSender kafkaSender;

    private OutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxWorker();
        try {
            var repoField = OutboxWorker.class.getDeclaredField("outboxRepository");
            repoField.setAccessible(true);
            repoField.set(worker, outboxRepository);

            var kafkaField = OutboxWorker.class.getDeclaredField("kafkaSender");
            kafkaField.setAccessible(true);
            kafkaField.set(worker, kafkaSenderInstance);

            var batchSizeField = OutboxWorker.class.getDeclaredField("batchSize");
            batchSizeField.setAccessible(true);
            batchSizeField.set(worker, 100);

            var enabledField = OutboxWorker.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(worker, true);

            var kafkaEnabledField = OutboxWorker.class.getDeclaredField("kafkaEnabled");
            kafkaEnabledField.setAccessible(true);
            kafkaEnabledField.set(worker, false);

            var cleanupDaysField = OutboxWorker.class.getDeclaredField("cleanupDays");
            cleanupDaysField.setAccessible(true);
            cleanupDaysField.set(worker, 7);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Default: no Kafka sender bean
        lenient().when(kafkaSenderInstance.isUnsatisfied()).thenReturn(true);
    }

    private OutboxEntry createPendingEntry() {
        return new OutboxEntry(
                TenantId.of(UUID.randomUUID()),
                "INVOICE",
                UUID.randomUUID(),
                "InvoiceIssued",
                "{\"test\":\"data\"}"
        );
    }

    private void setKafkaEnabled(boolean value) {
        try {
            var f = OutboxWorker.class.getDeclaredField("kafkaEnabled");
            f.setAccessible(true);
            f.set(worker, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("Process Pending Events")
    class ProcessPendingEvents {

        @Test
        @DisplayName("should process pending events")
        void shouldProcessPendingEvents() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));

            worker.processPendingEvents();

            verify(outboxRepository).findPending(100);
            verify(outboxRepository, times(2)).update(any(OutboxEntry.class));
        }

        @Test
        @DisplayName("should handle empty pending list")
        void shouldHandleEmptyPendingList() {
            when(outboxRepository.findPending(100)).thenReturn(List.of());

            worker.processPendingEvents();

            verify(outboxRepository).findPending(100);
            verify(outboxRepository, never()).update(any());
        }

        @Test
        @DisplayName("should not process when disabled")
        void shouldNotProcessWhenDisabled() throws Exception {
            var enabledField = OutboxWorker.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(worker, false);

            worker.processPendingEvents();

            verify(outboxRepository, never()).findPending(anyInt());
        }

        @Test
        @DisplayName("should handle repository exception gracefully")
        void shouldHandleRepositoryException() {
            when(outboxRepository.findPending(100))
                    .thenThrow(new RuntimeException("Table ar_outbox not found"));

            assertDoesNotThrow(() -> worker.processPendingEvents());
        }

        @Test
        @DisplayName("should handle general exception")
        void shouldHandleGeneralException() {
            when(outboxRepository.findPending(100))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertDoesNotThrow(() -> worker.processPendingEvents());
        }
    }

    @Nested
    @DisplayName("Kafka optional emit")
    class KafkaOptionalEmit {

        @Test
        @DisplayName("should not call sender when kafka-enabled=false")
        void shouldNotCallSenderWhenDisabled() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));
            setKafkaEnabled(false);

            worker.processPendingEvents();

            verify(kafkaSender, never()).send(any());
            assertEquals(1, worker.getPublishedCount());
        }

        @Test
        @DisplayName("should call sender when kafka-enabled=true and bean present")
        void shouldCallSenderWhenEnabledAndPresent() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));
            setKafkaEnabled(true);
            when(kafkaSenderInstance.isUnsatisfied()).thenReturn(false);
            when(kafkaSenderInstance.get()).thenReturn(kafkaSender);

            worker.processPendingEvents();

            verify(kafkaSender).send(entry);
            assertEquals(1, worker.getPublishedCount());
            assertTrue(worker.isKafkaEnabled());
        }

        @Test
        @DisplayName("should log and still publish when kafka-enabled but no sender")
        void shouldLogWhenEnabledButNoSender() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));
            setKafkaEnabled(true);
            when(kafkaSenderInstance.isUnsatisfied()).thenReturn(true);

            worker.processPendingEvents();

            verify(kafkaSender, never()).send(any());
            assertEquals(1, worker.getPublishedCount());
        }
    }

    @Nested
    @DisplayName("Cleanup")
    class Cleanup {

        @Test
        @DisplayName("should cleanup old published entries")
        void shouldCleanupOldEntries() {
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents(); // mark ready

            when(outboxRepository.deletePublishedOlderThan(any(Instant.class))).thenReturn(5);

            worker.cleanupOldEntries();

            verify(outboxRepository).deletePublishedOlderThan(any(Instant.class));
        }

        @Test
        @DisplayName("should skip cleanup when not ready")
        void shouldSkipCleanupWhenNotReady() {
            worker.cleanupOldEntries();

            verify(outboxRepository, never()).deletePublishedOlderThan(any());
        }

        @Test
        @DisplayName("should handle cleanup exception")
        void shouldHandleCleanupException() {
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();

            when(outboxRepository.deletePublishedOlderThan(any(Instant.class)))
                    .thenThrow(new RuntimeException("cleanup failed"));

            assertDoesNotThrow(() -> worker.cleanupOldEntries());
        }
    }

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should return pending count when ready")
        void shouldReturnPendingCount() {
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();

            when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(10L);

            assertEquals(10L, worker.getPendingCount());
        }

        @Test
        @DisplayName("should return failed count when ready")
        void shouldReturnFailedCount() {
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();

            when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(3L);

            assertEquals(3L, worker.getFailedInDbCount());
        }

        @Test
        @DisplayName("should return 0 pending when not ready")
        void shouldReturnZeroPendingWhenNotReady() {
            assertEquals(0L, worker.getPendingCount());
        }

        @Test
        @DisplayName("should return 0 failed when count throws")
        void shouldReturnZeroFailedWhenThrows() {
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();

            when(outboxRepository.countByStatus(OutboxStatus.FAILED))
                    .thenThrow(new RuntimeException("db down"));

            assertEquals(0L, worker.getFailedInDbCount());
        }

        @Test
        @DisplayName("should track published and failed counts")
        void shouldTrackCounts() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));

            worker.processPendingEvents();

            assertEquals(1, worker.getPublishedCount());
            assertEquals(0, worker.getFailedCount());
            assertTrue(worker.isDatabaseReady());
        }

        @Test
        @DisplayName("should mark failed on process error")
        void shouldMarkFailedOnError() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));
            doThrow(new RuntimeException("update failed"))
                    .when(outboxRepository).update(any(OutboxEntry.class));

            worker.processPendingEvents();

            assertEquals(1, worker.getFailedCount());
        }
    }
}