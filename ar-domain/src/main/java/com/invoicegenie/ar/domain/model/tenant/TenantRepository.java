package com.invoicegenie.ar.domain.model.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: tenant registry persistence.
 */
public interface TenantRepository {

    void save(Tenant tenant);

    Optional<Tenant> findById(UUID id);

    Optional<Tenant> findByCode(String code);

    List<Tenant> findAll();

    List<Tenant> findByStatus(TenantStatus status);

    boolean existsByCode(String code);
}
