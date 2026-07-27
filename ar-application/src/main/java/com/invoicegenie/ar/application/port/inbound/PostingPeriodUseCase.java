package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.period.PostingPeriod;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: AR posting period admin + open-period guard (PP-025).
 */
public interface PostingPeriodUseCase {

    List<PostingPeriod> list(TenantId tenantId);

    PostingPeriod open(TenantId tenantId, LocalDate start, LocalDate end, String notes);

    Optional<PostingPeriod> close(TenantId tenantId, UUID periodId, String closedBy);

    Optional<PostingPeriod> reopen(TenantId tenantId, UUID periodId);

    /**
     * Rejects when periods exist for tenant and {@code onDate} is not in an OPEN period.
     */
    void assertOpenFor(TenantId tenantId, LocalDate onDate);
}
