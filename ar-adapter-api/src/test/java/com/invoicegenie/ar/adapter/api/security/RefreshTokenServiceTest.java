package com.invoicegenie.ar.adapter.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RefreshTokenService hashing")
class RefreshTokenServiceTest {

    @Test
    void hashIsStableAndNotRaw() {
        String raw = "sample-refresh-token-value";
        String h1 = RefreshTokenService.hash(raw);
        String h2 = RefreshTokenService.hash(raw);
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
        assertNotEquals(raw, h1);
        assertTrue(h1.matches("[0-9a-f]+"));
    }
}