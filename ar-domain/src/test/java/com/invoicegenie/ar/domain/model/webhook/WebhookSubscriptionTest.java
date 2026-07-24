package com.invoicegenie.ar.domain.model.webhook;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebhookSubscriptionTest {

    @Test
    void createAndMatch() {
        var w = WebhookSubscription.create("https://hooks.example.com/ar", "sec", "InvoiceIssued,PaymentRecorded");
        assertTrue(w.isActive());
        assertTrue(w.matches("InvoiceIssued"));
        assertFalse(w.matches("Other"));
        w.deactivate();
        assertFalse(w.isActive());
    }

    @Test
    void starMatchesAll() {
        var w = WebhookSubscription.create("http://localhost/hook", null, "*");
        assertTrue(w.matches("Anything"));
    }

    @Test
    void requiresHttpUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookSubscription.create("ftp://x", null, "*"));
    }
}