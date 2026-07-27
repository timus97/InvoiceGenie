package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.period.PostingPeriod;
import com.invoicegenie.ar.domain.model.period.PostingPeriodRepository;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PeriodCloseGuard")
@ExtendWith(MockitoExtension.class)
class PeriodCloseGuardTest {

    @Mock PostingPeriodRepository repo;

    @Test
    @DisplayName("allows when tenant has no periods")
    void allowsWhenNoPeriods() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        when(repo.hasAnyPeriods(tid)).thenReturn(false);
        PeriodCloseGuard guard = new PeriodCloseGuard(repo);
        assertDoesNotThrow(() -> guard.assertOpenFor(tid, LocalDate.now()));
    }

    @Test
    @DisplayName("allows when open period covers today")
    void allowsOpenCovering() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        LocalDate today = LocalDate.now();
        when(repo.hasAnyPeriods(tid)).thenReturn(true);
        when(repo.findOpenCovering(tid, today)).thenReturn(Optional.of(
                PostingPeriod.open(tid, today.minusDays(1), today.plusDays(1), null)));
        PeriodCloseGuard guard = new PeriodCloseGuard(repo);
        assertDoesNotThrow(() -> guard.assertOpenFor(tid, today));
    }

    @Test
    @DisplayName("rejects when periods exist but none open covers date")
    void rejectsClosed() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        LocalDate today = LocalDate.now();
        when(repo.hasAnyPeriods(tid)).thenReturn(true);
        when(repo.findOpenCovering(tid, today)).thenReturn(Optional.empty());
        PeriodCloseGuard guard = new PeriodCloseGuard(repo);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.assertOpenFor(tid, today));
        assertTrue(ex.getMessage().contains("Posting period"));
    }
}
