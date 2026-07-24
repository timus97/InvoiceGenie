package com.invoicegenie.ar.adapter.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SsrfUrlValidatorTest {

    @Test
    void allowsPublicHttpsHostname() {
        // May not resolve offline; hostname policy alone must not block example.com
        assertDoesNotThrow(() -> SsrfUrlValidator.validateHttpUrl("https://example.com/ar-hook"));
    }

    @Test
    void blocksLocalhost() {
        assertThrows(IllegalArgumentException.class,
                () -> SsrfUrlValidator.validateHttpUrl("http://localhost/hook"));
    }

    @Test
    void blocksLoopbackIp() {
        assertThrows(IllegalArgumentException.class,
                () -> SsrfUrlValidator.validateHttpUrl("http://127.0.0.1/hook"));
    }

    @Test
    void blocksMetadataIp() {
        assertThrows(IllegalArgumentException.class,
                () -> SsrfUrlValidator.validateHttpUrl("http://169.254.169.254/latest/meta-data"));
    }

    @Test
    void requiresHttpScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> SsrfUrlValidator.validateHttpUrl("ftp://example.com/x"));
    }
}