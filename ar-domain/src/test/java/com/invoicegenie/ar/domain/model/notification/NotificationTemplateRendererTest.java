package com.invoicegenie.ar.domain.model.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationTemplateRenderer")
class NotificationTemplateRendererTest {

    @Test
    @DisplayName("replaces known variables")
    void renderKnownVars() {
        String out = NotificationTemplateRenderer.render(
                "Hello {{customerName}}, invoice {{invoiceNumber}} for {{total}} {{currency}}",
                Map.of("customerName", "Acme", "invoiceNumber", "INV-1", "total", "100.00", "currency", "USD"));
        assertEquals("Hello Acme, invoice INV-1 for 100.00 USD", out);
    }

    @Test
    @DisplayName("unknown variables become empty")
    void unknownVarsEmpty() {
        String out = NotificationTemplateRenderer.render("Hi {{missing}}!", Map.of("other", "x"));
        assertEquals("Hi !", out);
    }

    @Test
    @DisplayName("null template returns null")
    void nullTemplate() {
        assertNull(NotificationTemplateRenderer.render(null, Map.of()));
    }

    @Test
    @DisplayName("supports whitespace inside braces")
    void whitespaceInBraces() {
        String out = NotificationTemplateRenderer.render("{{ name }}", Map.of("name", "Bob"));
        assertEquals("Bob", out);
    }
}
