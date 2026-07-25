package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationIdempotencyKeys")
class NotificationIdempotencyKeysTest {

    private final InvoiceId invoiceId = InvoiceId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Test
    @DisplayName("invoice issued key has no qualifier")
    void invoiceIssued() {
        String key = NotificationIdempotencyKeys.forInvoiceIssued(invoiceId, NotificationChannel.EMAIL);
        assertEquals("notify:INVOICE_ISSUED:11111111-1111-1111-1111-111111111111:EMAIL", key);
    }

    @Test
    @DisplayName("payment reminder includes due date")
    void paymentReminder() {
        String key = NotificationIdempotencyKeys.forPaymentReminder(
                invoiceId, NotificationChannel.EMAIL, LocalDate.of(2026, 7, 30));
        assertEquals("notify:PAYMENT_REMINDER:11111111-1111-1111-1111-111111111111:EMAIL:due:2026-07-30", key);
    }

    @Test
    @DisplayName("dunning includes level")
    void dunning() {
        String key = NotificationIdempotencyKeys.forDunningNotice(invoiceId, NotificationChannel.WHATSAPP, 2);
        assertEquals("notify:DUNNING_NOTICE:11111111-1111-1111-1111-111111111111:WHATSAPP:L2", key);
    }

    @Test
    @DisplayName("same inputs produce identical keys")
    void stable() {
        String a = NotificationIdempotencyKeys.forInvoiceIssued(invoiceId, NotificationChannel.EMAIL);
        String b = NotificationIdempotencyKeys.forInvoiceIssued(invoiceId, NotificationChannel.EMAIL);
        assertEquals(a, b);
        assertNotEquals(a, NotificationIdempotencyKeys.forInvoiceIssued(invoiceId, NotificationChannel.WHATSAPP));
    }
}
