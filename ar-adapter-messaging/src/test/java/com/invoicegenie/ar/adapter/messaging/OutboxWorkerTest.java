package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.ar.domain.model.outbox.OutboxStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("OutboxWorker")
@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxWorker();
        // Use reflection to inject mocks and config
        try {
            var repoField = OutboxWorker.class.getDeclaredField("outboxRepository");
            repoField.setAccessible(true);
            repoField.set(worker, outboxRepository);
            
            var batchSizeField = OutboxWorker.class.getDeclaredField("batchSize");
            batchSizeField.setAccessible(true);
            batchSizeField.set(worker, 100);
            
            var enabledField = OutboxWorker.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(worker, true);
            
            var cleanupDaysField = OutboxWorker.class.getDeclaredField("cleanupDays");
            cleanupDaysField.setAccessible(true);
            cleanupDaysField.set(worker, 7);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        void shouldNotProcessWhenDisabled() {
            try {
                var enabledField = OutboxWorker.class.getDeclaredField("enabled");
                enabledField.setAccessible(true);
                enabledField.set(worker, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            worker.processPendingEvents();
            
            verify(outboxRepository, never()).findPending(anyInt());
        }

        @Test
        @DisplayName("should handle repository exception")
        void shouldHandleRepositoryException() {
            when(outboxRepository.findPending(100))
                    .thenThrow(new RuntimeException("Database error"));
            
            // Should not throw
            assertDoesNotThrow(() -> worker.processPendingEvents());
        }

        @Test
        @DisplayName("should handle table not found exception")
        void shouldHandleTableNotFoundException() {
            when(outboxRepository.findPending(100))
                    .thenThrow(new RuntimeException("Table not found"));
            
            // Should not throw
            assertDoesNotThrow(() -> worker.processPendingEvents());
        }
    }

    @Nested
    @DisplayName("Cleanup Old Entries")
    class CleanupOldEntries {

        @Test
        @DisplayName("should cleanup old published entries")
        void shouldCleanupOldPublishedEntries() {
            // First call to processPendingEvents to set databaseReady
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();
            
            when(outboxRepository.deletePublishedOlderThan(any(Instant.class))).thenReturn(5);
            
            worker.cleanupOldEntries();
            
            verify(outboxRepository).deletePublishedOlderThan(any(Instant.class));
        }

        @Test
        @DisplayName("should not cleanup when disabled")
        void shouldNotCleanupWhenDisabled() {
            try {
                var enabledField = OutboxWorker.class.getDeclaredField("enabled");
                enabledField.setAccessible(true);
                enabledField.set(worker, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            worker.cleanupOldEntries();
            
            verify(outboxRepository, never()).deletePublishedOlderThan(any());
        }

        @Test
        @DisplayName("should handle cleanup exception")
        void shouldHandleCleanupException() {
            // First call to processPendingEvents to set databaseReady
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();
            
            when(outboxRepository.deletePublishedOlderThan(any(Instant.class)))
                    .thenThrow(new RuntimeException("Cleanup error"));
            
            // Should not throw
            assertDoesNotThrow(() -> worker.cleanupOldEntries());
        }
    }

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should return published count")
        void shouldReturnPublishedCount() {
            assertEquals(0, worker.getPublishedCount());
        }

        @Test
        @DisplayName("should return failed count")
        void shouldReturnFailedCount() {
            assertEquals(0, worker.getFailedCount());
        }

        @Test
        @DisplayName("should return pending count when database ready")
        void shouldReturnPendingCountWhenDatabaseReady() {
            // First call to processPendingEvents to set databaseReady
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();
            
            when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(10L);
            
            assertEquals(10, worker.getPendingCount());
        }

        @Test
        @DisplayName("should return zero pending count when database not ready")
        void shouldReturnZeroPendingCountWhenDatabaseNotReady() {
            assertEquals(0, worker.getPendingCount());
        }

        @Test
        @DisplayName("should return failed in db count when database ready")
        void shouldReturnFailedInDbCountWhenDatabaseReady() {
            // First call to processPendingEvents to set databaseReady
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();
            
            when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(3L);
            
            assertEquals(3, worker.getFailedInDbCount());
        }

        @Test
        @DisplayName("should return zero failed count when database not ready")
        void shouldReturnZeroFailedCountWhenDatabaseNotReady() {
            assertEquals(0, worker.getFailedInDbCount());
        }

        @Test
        @DisplayName("should handle exception in pending count")
        void shouldHandleExceptionInPendingCount() {
            // First call to processPendingEvents to set databaseReady
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            worker.processPendingEvents();
            
            when(outboxRepository.countByStatus(OutboxStatus.PENDING))
                    .thenThrow(new RuntimeException("Count error"));
            
            assertEquals(0, worker.getPendingCount());
        }
    }

    @Nested
    @DisplayName("Database Ready State")
    class DatabaseReadyState {

        @Test
        @DisplayName("should not be ready initially")
        void shouldNotBeReadyInitially() {
            assertFalse(worker.isDatabaseReady());
        }

        @Test
        @DisplayName("should be ready after first successful query")
        void shouldBeReadyAfterFirstSuccessfulQuery() {
            when(outboxRepository.findPending(100)).thenReturn(List.of());
            
            worker.processPendingEvents();
            
            assertTrue(worker.isDatabaseReady());
        }
    }

    @Nested
    @DisplayName("Entry Processing")
    class EntryProcessing {

        @Test
        @DisplayName("should increment published count on success")
        void shouldIncrementPublishedCountOnSuccess() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));
            
            worker.processPendingEvents();
            
            assertEquals(1, worker.getPublishedCount());
        }

        @Test
        @DisplayName("should mark entry as processing before publishing")
        void shouldMarkEntryAsProcessingBeforePublishing() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));
            
            worker.processPendingEvents();
            
            // Entry should be updated twice: once for processing, once for published
            verify(outboxRepository, times(2)).update(any(OutboxEntry.class));
        }

        @Test
        @DisplayName("should handle processing failure")
        void shouldHandleProcessingFailure() {
            OutboxEntry entry = createPendingEntry();
            when(outboxRepository.findPending(100)).thenReturn(List.of(entry));
            doThrow(new RuntimeException("Update failed"))
                    .when(outboxRepository).update(any(OutboxEntry.class));
            
            worker.processPendingEvents();
            
            assertEquals(1, worker.getFailedCount());
        }
    }
}
