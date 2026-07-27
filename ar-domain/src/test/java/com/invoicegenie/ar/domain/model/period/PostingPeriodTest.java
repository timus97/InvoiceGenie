package com.invoicegenie.ar.domain.model.period;

import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostingPeriodTest {

    @Test
    void openCloseReopen() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        PostingPeriod p = PostingPeriod.open(tid, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "Jan");
        assertTrue(p.isOpen());
        assertTrue(p.contains(LocalDate.of(2026, 1, 15)));
        assertFalse(p.contains(LocalDate.of(2026, 2, 1)));
        p.close("admin");
        assertEquals(PostingPeriodStatus.CLOSED, p.getStatus());
        assertNotNull(p.getClosedAt());
        p.reopen();
        assertTrue(p.isOpen());
        assertNull(p.getClosedAt());
    }

    @Test
    void rejectsInvalidRange() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () ->
                PostingPeriod.open(tid, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null));
    }
}
