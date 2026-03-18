package com.invoicegenie.ar.domain.model.invoice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceLifecycleEngineTest {

    @Test
    void allowsValidTransitions() {
        InvoiceLifecycleEngine engine = new InvoiceLifecycleEngine();
        assertDoesNotThrow(() -> engine.assertTransitionAllowed(InvoiceStatus.DRAFT, InvoiceStatus.ISSUED));
        assertDoesNotThrow(() -> engine.assertTransitionAllowed(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID));
        assertDoesNotThrow(() -> engine.assertTransitionAllowed(InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID));
        assertDoesNotThrow(() -> engine.assertTransitionAllowed(InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE));
        assertDoesNotThrow(() -> engine.assertTransitionAllowed(InvoiceStatus.OVERDUE, InvoiceStatus.WRITTEN_OFF));
    }

    @Test
    void rejectsInvalidTransitions() {
        InvoiceLifecycleEngine engine = new InvoiceLifecycleEngine();
        assertThrows(IllegalStateException.class, () -> engine.assertTransitionAllowed(InvoiceStatus.DRAFT, InvoiceStatus.PAID));
        assertThrows(IllegalStateException.class, () -> engine.assertTransitionAllowed(InvoiceStatus.PAID, InvoiceStatus.ISSUED));
        assertThrows(IllegalStateException.class, () -> engine.assertTransitionAllowed(InvoiceStatus.WRITTEN_OFF, InvoiceStatus.PAID));
    }

    @Test
    void rejectsNullStatus() {
        InvoiceLifecycleEngine engine = new InvoiceLifecycleEngine();
        assertThrows(IllegalArgumentException.class, () -> engine.assertTransitionAllowed(null, InvoiceStatus.ISSUED));
    }

    @Test
    void rejectsSameStatus() {
        InvoiceLifecycleEngine engine = new InvoiceLifecycleEngine();
        assertThrows(IllegalStateException.class, () -> engine.assertTransitionAllowed(InvoiceStatus.ISSUED, InvoiceStatus.ISSUED));
    }
}
