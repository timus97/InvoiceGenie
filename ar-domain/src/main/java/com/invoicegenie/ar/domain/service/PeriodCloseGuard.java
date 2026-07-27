package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.period.PostingPeriodRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Guard: reject AR mutations when today is outside an OPEN posting period (PP-025).
 *
 * <p>Backward compatible: tenants with zero period rows are unrestricted.
 */
public final class PeriodCloseGuard {

    private final PostingPeriodRepository periodRepository;

    public PeriodCloseGuard(PostingPeriodRepository periodRepository) {
        this.periodRepository = Objects.requireNonNull(periodRepository);
    }

    /**
     * @throws IllegalStateException when periods exist but none OPEN covers {@code onDate}
     */
    public void assertOpenFor(TenantId tenantId, LocalDate onDate) {
        Objects.requireNonNull(tenantId, "tenantId");
        LocalDate day = onDate != null ? onDate : LocalDate.now();
        if (!periodRepository.hasAnyPeriods(tenantId)) {
            return;
        }
        if (periodRepository.findOpenCovering(tenantId, day).isEmpty()) {
            throw new IllegalStateException(
                    "Posting period closed or missing for date " + day
                            + "; open an AR period before issuing invoices or recording payments");
        }
    }
}
