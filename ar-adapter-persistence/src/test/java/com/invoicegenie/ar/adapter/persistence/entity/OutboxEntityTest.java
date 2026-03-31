package com.invoicegenie.ar.adapter.persistence.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEntityTest {

    @Test
    void testDefaultConstructor() {
        OutboxEntity entity = new OutboxEntity();
        
        assertNull(entity.getId());
        assertNull(entity.getTenantId());
        assertNull(entity.getAggregateType());
        assertNull(entity.getAggregateId());
        assertNull(entity.getEventType());
        assertNull(entity.getPayload());
        assertNotNull(entity.getCreatedAt());
        assertNull(entity.getPublishedAt());
        assertEquals(OutboxEntity.OutboxStatus.PENDING, entity.getStatus());
        assertEquals(0, entity.getRetryCount());
        assertNull(entity.getLastError());
    }

    @Test
    void testParameterizedConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        
        OutboxEntity entity = new OutboxEntity(
            id, tenantId, "Invoice", aggregateId, 
            "InvoiceIssued", "{\"invoiceId\":\"" + aggregateId + "\"}"
        );
        
        assertEquals(id, entity.getId());
        assertEquals(tenantId, entity.getTenantId());
        assertEquals("Invoice", entity.getAggregateType());
        assertEquals(aggregateId, entity.getAggregateId());
        assertEquals("InvoiceIssued", entity.getEventType());
        assertEquals("{\"invoiceId\":\"" + aggregateId + "\"}", entity.getPayload());
        assertNotNull(entity.getCreatedAt());
        assertEquals(OutboxEntity.OutboxStatus.PENDING, entity.getStatus());
        assertEquals(0, entity.getRetryCount());
    }

    @Test
    void testMarkProcessing() {
        OutboxEntity entity = new OutboxEntity();
        entity.markProcessing();
        
        assertEquals(OutboxEntity.OutboxStatus.PROCESSING, entity.getStatus());
    }

    @Test
    void testMarkPublished() {
        OutboxEntity entity = new OutboxEntity();
        entity.markPublished();
        
        assertEquals(OutboxEntity.OutboxStatus.PUBLISHED, entity.getStatus());
        assertNotNull(entity.getPublishedAt());
    }

    @Test
    void testMarkFailed_StillPending() {
        OutboxEntity entity = new OutboxEntity();
        entity.markFailed("Connection refused");
        
        assertEquals(1, entity.getRetryCount());
        assertEquals("Connection refused", entity.getLastError());
        assertEquals(OutboxEntity.OutboxStatus.PENDING, entity.getStatus());
        assertTrue(entity.canRetry());
    }

    @Test
    void testMarkFailed_MaxRetriesReached() {
        OutboxEntity entity = new OutboxEntity();
        
        // Fail 5 times (max retries)
        for (int i = 0; i < 5; i++) {
            entity.markFailed("Error " + i);
        }
        
        assertEquals(5, entity.getRetryCount());
        assertEquals(OutboxEntity.OutboxStatus.FAILED, entity.getStatus());
        assertFalse(entity.canRetry());
    }

    @Test
    void testCanRetry() {
        OutboxEntity entity = new OutboxEntity();
        
        assertTrue(entity.canRetry());
        
        for (int i = 0; i < 4; i++) {
            entity.markFailed("Error");
            assertTrue(entity.canRetry());
        }
        
        entity.markFailed("Final error");
        assertFalse(entity.canRetry());
    }

    @Test
    void testGetMaxRetries() {
        assertEquals(5, OutboxEntity.getMaxRetries());
    }

    @Test
    void testGettersAndSetters() {
        OutboxEntity entity = new OutboxEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant published = now.plusSeconds(60);
        
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setAggregateType("Payment");
        entity.setAggregateId(aggregateId);
        entity.setEventType("PaymentRecorded");
        entity.setPayload("{\"amount\":100}");
        entity.setCreatedAt(now);
        entity.setPublishedAt(published);
        entity.setStatus(OutboxEntity.OutboxStatus.PUBLISHED);
        entity.setRetryCount(3);
        entity.setLastError("Previous error");
        
        assertEquals(id, entity.getId());
        assertEquals(tenantId, entity.getTenantId());
        assertEquals("Payment", entity.getAggregateType());
        assertEquals(aggregateId, entity.getAggregateId());
        assertEquals("PaymentRecorded", entity.getEventType());
        assertEquals("{\"amount\":100}", entity.getPayload());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(published, entity.getPublishedAt());
        assertEquals(OutboxEntity.OutboxStatus.PUBLISHED, entity.getStatus());
        assertEquals(3, entity.getRetryCount());
        assertEquals("Previous error", entity.getLastError());
    }

    @Test
    void testOutboxStatusEnum() {
        assertEquals(4, OutboxEntity.OutboxStatus.values().length);
        assertEquals(OutboxEntity.OutboxStatus.PENDING, 
            OutboxEntity.OutboxStatus.valueOf("PENDING"));
        assertEquals(OutboxEntity.OutboxStatus.PROCESSING, 
            OutboxEntity.OutboxStatus.valueOf("PROCESSING"));
        assertEquals(OutboxEntity.OutboxStatus.PUBLISHED, 
            OutboxEntity.OutboxStatus.valueOf("PUBLISHED"));
        assertEquals(OutboxEntity.OutboxStatus.FAILED, 
            OutboxEntity.OutboxStatus.valueOf("FAILED"));
    }

    @Test
    void testMarkFailed_IncrementalRetries() {
        OutboxEntity entity = new OutboxEntity();
        
        entity.markFailed("Error 1");
        assertEquals(1, entity.getRetryCount());
        assertEquals("Error 1", entity.getLastError());
        
        entity.markFailed("Error 2");
        assertEquals(2, entity.getRetryCount());
        assertEquals("Error 2", entity.getLastError());
        
        entity.markFailed("Error 3");
        assertEquals(3, entity.getRetryCount());
        assertEquals("Error 3", entity.getLastError());
    }
}
