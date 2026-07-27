package com.invoicegenie.ar.domain.model.period;

import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: AR posting periods (PP-025).
 */
public interface PostingPeriodRepository {

    void save(TenantId tenantId, PostingPeriod period);

    Optional<PostingPeriod> findById(TenantId tenantId, UUID id);

    List<PostingPeriod> findAllByTenant(TenantId tenantId);

    /**
     * @return true if the tenant has any period rows (open or closed).
     */
    boolean hasAnyPeriods(TenantId tenantId);

    /**
     * Find an OPEN period that contains {@code day}, if any.
     */
    Optional<PostingPeriod> findOpenCovering(TenantId tenantId, LocalDate day);
}
