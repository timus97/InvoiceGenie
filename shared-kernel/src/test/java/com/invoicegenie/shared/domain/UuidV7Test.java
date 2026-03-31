package com.invoicegenie.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UuidV7Test {

    @Test
    void generate_ReturnsValidUuid() {
        UUID uuid = UuidV7.generate();
        
        assertNotNull(uuid);
        assertEquals(7, uuid.version());
    }

    @Test
    void generate_ReturnsUniqueIds() {
        int count = 10000;
        List<UUID> ids = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ids.add(UuidV7.generate());
        }
        
        // All should be unique
        long uniqueCount = ids.stream().distinct().count();
        assertEquals(count, uniqueCount);
    }

    @Test
    void generate_IdsAreTimeOrdered() {
        // Generate multiple UUIDs with small delays
        UUID id1 = UuidV7.generate();
        try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        UUID id2 = UuidV7.generate();
        try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        UUID id3 = UuidV7.generate();
        
        // UUID v7 should be sortable by time
        assertTrue(id1.compareTo(id2) < 0, "id1 should be less than id2");
        assertTrue(id2.compareTo(id3) < 0, "id2 should be less than id3");
    }

    @Test
    void generateAsString_ReturnsValidString() {
        String uuidStr = UuidV7.generateAsString();
        
        assertNotNull(uuidStr);
        assertEquals(36, uuidStr.length());
        // Should be valid UUID format
        assertDoesNotThrow(() -> UUID.fromString(uuidStr));
    }

    @Test
    void extractTimestamp_ReturnsCorrectTimestamp() {
        long before = System.currentTimeMillis();
        UUID uuid = UuidV7.generate();
        long after = System.currentTimeMillis();
        
        long extracted = UuidV7.extractTimestamp(uuid);
        
        assertTrue(extracted >= before - 100, "Extracted timestamp should be close to current time");
        assertTrue(extracted <= after + 100, "Extracted timestamp should be close to current time");
    }

    @Test
    void extractTimestamp_ReturnsZeroForNonV7() {
        UUID randomUuid = UUID.randomUUID(); // UUID v4
        
        long timestamp = UuidV7.extractTimestamp(randomUuid);
        
        assertEquals(0, timestamp);
    }

    @Test
    void isUuidV7_ReturnsTrueForV7() {
        UUID uuidV7 = UuidV7.generate();
        
        assertTrue(UuidV7.isUuidV7(uuidV7));
    }

    @Test
    void isUuidV7_ReturnsFalseForV4() {
        UUID uuidV4 = UUID.randomUUID();
        
        assertFalse(UuidV7.isUuidV7(uuidV4));
    }

    @Test
    void isUuidV7_ReturnsFalseForNull() {
        assertFalse(UuidV7.isUuidV7(null));
    }

    @Test
    void generate_IsThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 1000;
        List<Thread> threads = new ArrayList<>();
        List<UUID> allIds = new ArrayList<>();
        
        for (int t = 0; t < threadCount; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < idsPerThread; i++) {
                    synchronized (allIds) {
                        allIds.add(UuidV7.generate());
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        // All should be unique
        long uniqueCount = allIds.stream().distinct().count();
        assertEquals(threadCount * idsPerThread, uniqueCount);
    }
}
