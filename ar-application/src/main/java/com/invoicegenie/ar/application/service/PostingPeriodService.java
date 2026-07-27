package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PostingPeriodUseCase;
import com.invoicegenie.ar.domain.model.period.PostingPeriod;
import com.invoicegenie.ar.domain.model.period.PostingPeriodRepository;
import com.invoicegenie.ar.domain.service.PeriodCloseGuard;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: posting period admin + guard (PP-025).
 */
public class PostingPeriodService implements PostingPeriodUseCase {

    private final PostingPeriodRepository periodRepository;
    private final PeriodCloseGuard guard;

    public PostingPeriodService(PostingPeriodRepository periodRepository) {
        this.periodRepository = Objects.requireNonNull(periodRepository);
        this.guard = new PeriodCloseGuard(periodRepository);
    }

    @Override
    public List<PostingPeriod> list(TenantId tenantId) {
        return periodRepository.findAllByTenant(tenantId);
    }

    @Override
    public PostingPeriod open(TenantId tenantId, LocalDate start, LocalDate end, String notes) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("periodStart and periodEnd are required");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("periodEnd must be on or after periodStart");
        }
        PostingPeriod period = PostingPeriod.open(tenantId, start, end, notes);
        periodRepository.save(tenantId, period);
        return period;
    }

    @Override
    public Optional<PostingPeriod> close(TenantId tenantId, UUID periodId, String closedBy) {
        return periodRepository.findById(tenantId, periodId).map(p -> {
            p.close(closedBy != null ? closedBy : "system");
            periodRepository.save(tenantId, p);
            return p;
        });
    }

    @Override
    public Optional<PostingPeriod> reopen(TenantId tenantId, UUID periodId) {
        return periodRepository.findById(tenantId, periodId).map(p -> {
            p.reopen();
            periodRepository.save(tenantId, p);
            return p;
        });
    }

    @Override
    public void assertOpenFor(TenantId tenantId, LocalDate onDate) {
        guard.assertOpenFor(tenantId, onDate);
    }
}
