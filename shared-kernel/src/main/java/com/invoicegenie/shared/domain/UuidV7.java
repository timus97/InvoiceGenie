package com.invoicegenie.shared.domain;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UUID v7 generator for time-ordered, partition-friendly identifiers.
 * 
 * <p>UUID v7 layout:
 * <ul>
 *   <li>48 bits: Unix timestamp in milliseconds</li>
 *   <li>4 bits: version (7)</li>
 *   <li>12 bits: random</li>
 *   <li>2 bits: variant (10)</li>
 *   <li>62 bits: random</li>
 * </ul>
 * 
 * <p>Benefits over UUID v4:
 * <ul>
 *   <li>Time-ordered: better database index locality</li>
 *   <li>Reduced fragmentation in B-tree indexes</li>
 *   <li>Sortable by creation time</li>
 *   <li>Can be generated without coordination</li>
 * </ul>
 */
public final class UuidV7 {

    private UuidV7() {}

    /**
     * Generates a new UUID v7.
     * Thread-safe; uses ThreadLocalRandom for randomness.
     */
    public static UUID generate() {
        long timestamp = System.currentTimeMillis();
        
        // 48-bit timestamp in msb
        long msb = (timestamp << 16) | (ThreadLocalRandom.current().nextLong() & 0x0FFF);
        // Set version 7 in bits 12-15 of msb
        msb = (msb & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000007000L;
        
        // 64-bit lsb: 2-bit variant (10) + 62-bit random
        long lsb = (ThreadLocalRandom.current().nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        
        return new UUID(msb, lsb);
    }

    /**
     * Generates a new UUID v7 as string.
     */
    public static String generateAsString() {
        return generate().toString();
    }

    /**
     * Extracts the timestamp (milliseconds since epoch) from a UUID v7.
     * Returns 0 if the UUID is not v7.
     */
    public static long extractTimestamp(UUID uuid) {
        if (uuid.version() != 7) {
            return 0;
        }
        // Top 48 bits of msb contain the timestamp
        return uuid.getMostSignificantBits() >>> 16;
    }

    /**
     * Checks if a UUID is version 7.
     */
    public static boolean isUuidV7(UUID uuid) {
        return uuid != null && uuid.version() == 7;
    }
}
