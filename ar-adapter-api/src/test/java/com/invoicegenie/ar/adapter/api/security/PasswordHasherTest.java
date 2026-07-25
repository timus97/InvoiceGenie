package com.invoicegenie.ar.adapter.api.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PasswordHasher")
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAndVerifies() {
        String hash = hasher.hash("Admin123!");
        assertTrue(hasher.matches("Admin123!", hash));
        assertFalse(hasher.matches("wrong", hash));
    }
}